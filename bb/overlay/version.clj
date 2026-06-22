(ns overlay.version
  "Module for checking and reporting package version updates.
   Supports fetching latest tags or releases from GitHub and comparing
   them against the current version defined in default.nix."
  (:require
   [babashka.http-client :as http]
   [cheshire.core :as json]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [overlay.system :as sys]))

(defn parse-version-fallback
  "Helper function to parse the version of `package-name` directly from `default-nix-path`
   by scanning for the package declaration and searching for its version property.

   This is used as a fallback if `nix-instantiate` is not available or fails."
  [default-nix-path package-name]
  ;; Read all lines from default.nix
  (let [lines (str/split-lines (slurp default-nix-path))
        pattern (str package-name " = self.callPackage")]
    ;; Loop through the lines to find the package declaration
    (loop [remaining lines]
      (if (empty? remaining)
        (throw (ex-info (str "Could not parse " package-name " version from " default-nix-path)
                        {:package package-name :file default-nix-path}))
        (let [line (first remaining)]
          ;; If the line contains e.g. "eca = self.callPackage", check the next few lines
          (if (str/includes? line pattern)
            ;; Look at the next 3 lines for the version string
            (if-let [v-line (some (fn [l] (when (str/includes? l "version =") l))
                                  (take 3 (rest remaining)))]
              ;; Extract the version from version = "X.Y.Z";
              (second (re-find (re-pattern "version\\s*=\\s*\"([^\"]+)\"") v-line))
              (recur (rest remaining)))
            (recur (rest remaining))))))))

(defn current-property
  "Retrieves the currently defined property of `package-name` from the Nix overlay definition.
   First tries to evaluate using `nix-instantiate` to get the actual evaluated value.
   Falls back to parsing `default-nix-path` directly if `nix-instantiate` fails or is unavailable."
  ([package-name property-name] (current-property "default.nix" package-name property-name))
  ([default-nix-path package-name property-name]
   (try
     ;; Attempt to evaluate the property using nix-instantiate
     (let [{:keys [exit out err]} (shell/sh "nix-instantiate" "--eval" "-E"
                                            (str "let overlay = import ./" default-nix-path
                                                 "; pkgs = overlay (pkgs // { callPackage = path: args: args; }) {}; in pkgs."
                                                 package-name "." property-name))]
       (if (zero? exit)
         ;; Strip surrounding quotes from the evaluated Nix string
         (str/replace (str/trim out) "\"" "")
         (throw (Exception. err))))
     (catch Exception _
       ;; Fallback to manual file parsing if Nix command fails or is missing
       (let [lines (str/split-lines (slurp default-nix-path))
             pattern (str package-name " = self.callPackage")
             prop-pattern (re-pattern (str property-name "\\s*=\\s*\"([^\"]+)\""))]
         (loop [remaining lines]
           (if (empty? remaining)
             (throw (ex-info (str "Could not parse " package-name " " property-name " from " default-nix-path)
                             {:package package-name :file default-nix-path}))
             (let [line (first remaining)]
               (if (str/includes? line pattern)
                 (if-let [p-line (some (fn [l] (when (str/includes? l (str property-name " =")) l))
                                       (take 5 (rest remaining)))]
                   (second (re-find prop-pattern p-line))
                   (recur (rest remaining)))
                 (recur (rest remaining)))))))))))

(defn current-version
  "Retrieves the currently installed version of `package-name` from the Nix overlay definition.
   First tries to evaluate using `nix-instantiate` to get the actual evaluated value.
   Falls back to parsing `default-nix-path` directly if `nix-instantiate` fails or is unavailable.

   Arguments:
   - `package-name`: name of the package (e.g. \"eca\")
   - `default-nix-path` (optional): path to the nix file, defaults to \"default.nix\""
  ([package-name] (current-version "default.nix" package-name))
  ([default-nix-path package-name]
   (current-property default-nix-path package-name "version")))

(defn fetch-github-latest-version
  "Fetches the latest version of a GitHub repository via GitHub's public API.

   Arguments:
   - `repo`: GitHub repository path in \"owner/repo\" format (e.g. \"editor-code-assistant/eca\")

   Options:
   - `:strategy` - either `:release` (fetches from /releases/latest), `:tag` (fetches /tags and returns the first tag), or `:commit` (fetches latest commit SHA)
   - `:branch`   - string (default \"master\"), only used for `:commit` strategy
   - `:strip-v?` - boolean (default true), if true, strips leading 'v' from the version string.
   - `:headers`  - map of headers to pass to the HTTP request (defaults to User-Agent: babashka)"
  ([repo] (fetch-github-latest-version repo {}))
  ([repo {:keys [strategy strip-v? headers branch]
          :or {strategy :release
               strip-v? true
               headers {"User-Agent" "babashka"}
               branch "master"}}]
   (let [url (case strategy
               :release (str "https://api.github.com/repos/" repo "/releases/latest")
               :tag     (str "https://api.github.com/repos/" repo "/tags")
               :commit  (str "https://api.github.com/repos/" repo "/commits/" branch))
         resp (http/get url {:headers headers})
         _ (when-not (= 200 (:status resp))
             (throw (ex-info (str "HTTP error " (:status resp)) {:status (:status resp) :body (:body resp)})))
         body-parsed (json/parse-string (:body resp) true)
         raw-version (case strategy
                       :release (:tag_name body-parsed)
                       :tag     (if (seq body-parsed)
                                  (:name (first body-parsed))
                                  (throw (ex-info "No tags found" {:body body-parsed})))
                       :commit  (:sha body-parsed))]
     (if (and strip-v? raw-version)
       (str/replace raw-version #"^v" "")
       raw-version))))

(defn report-update-status
  "Compares current-version and latest-version for a package, and prints a user-friendly report.
   If a new version is available, it informs the user and provides an update instruction.

   Arguments:
   - `package-name`: package name in the overlay (e.g. \"eca\")
   - `current-version`: current version string
   - `latest-version`: latest version string

   Options:
   - `:update-instruction-fn`: custom fn `(fn [pkg ver])` returning the instruction string."
  ([package-name current-version latest-version]
   (report-update-status package-name current-version latest-version {}))
  ([package-name current-version latest-version {:keys [update-instruction-fn]}]
   (println "Current installed version of" package-name ":" current-version)
   (println "Latest available version of" package-name ":" latest-version)
   (if (not= current-version latest-version)
     (let [instruction-fn (or update-instruction-fn
                              (fn [pkg ver]
                                (str "To update, run: nix-shell -p common-updater-scripts --run \"update-source-version "
                                     pkg " " ver "\"")))
           instruction (instruction-fn package-name latest-version)]
       (println (str "★ A new version (" latest-version ") of " package-name " is available!"))
       (when (seq instruction)
         (println instruction))
       true)
     (do
       (println "✓" package-name "is up to date.")
       false))))

(defn check-github-package-update
  "Helper function to perform a complete update check for a GitHub-hosted package.
   Fetches current version from local Nix files, latest version from GitHub,
   compares them, and prints the report. Exits the process on connection failure.

   Arguments:
   - `package-name`: name of the package (e.g. \"eca\")
   - `repo`: GitHub repository path in \"owner/repo\" format (e.g. \"editor-code-assistant/eca\")
   - `opts`: options map passed to `fetch-github-latest-version` (e.g. `:strategy :tag` or `:strategy :release`)"
  ([package-name repo] (check-github-package-update package-name repo {}))
  ([package-name repo opts]
   (let [curr-ver (current-version package-name)
         latest-ver (try
                      (fetch-github-latest-version repo opts)
                      (catch Exception e
                        (binding [*out* *err*]
                          (println (str "Fatal: Could not connect to the GitHub API to check for " package-name " updates.")))
                        (System/exit 1)))]
     (report-update-status package-name curr-ver latest-ver))))

(defn check-github-commit-update
  "Helper function to perform a complete update check for a GitHub-hosted package pinned to a commit.
   Fetches current rev from local Nix files, latest commit SHA from GitHub,
   compares them, and prints the report. Exits the process on connection failure.

   Arguments:
   - `package-name`: name of the package (e.g. \"tree-sitter-convex-lisp\")
   - `repo`: GitHub repository path in \"owner/repo\" format
   - `opts`: options map passed to `fetch-github-latest-version` (e.g. `:branch \"main\"`)"
  ([package-name repo] (check-github-commit-update package-name repo {}))
  ([package-name repo opts]
   (let [curr-rev (current-property package-name "rev")
         latest-rev (try
                      (fetch-github-latest-version repo (assoc opts :strategy :commit))
                      (catch Exception e
                        (binding [*out* *err*]
                          (println (str "Fatal: Could not connect to the GitHub API to check for " package-name " updates.")))
                        (System/exit 1)))]
     (println "Current installed rev of" package-name ":" curr-rev)
     (println "Latest available rev of" package-name ":" latest-rev)
     (if (not= curr-rev latest-rev)
       (let [instruction-fn (or (:update-instruction-fn opts)
                                (fn [pkg rev]
                                  (str "To update, update the 'rev' attribute of " pkg " in default.nix to: " rev)))
             instruction (instruction-fn package-name latest-rev)]
         (println (str "★ A new commit (" (subs latest-rev 0 7) ") of " package-name " is available!"))
         (when (seq instruction)
           (println instruction))
         true)
       (do
         (println "✓" package-name "is up to date.")
         false)))))

(defn check-antigravity-cli-update
  "Checks if a new version of antigravity-cli is available by querying the release manifest server."
  []
  (let [platform (sys/platform)
        manifest-url (str "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/" platform ".json")
        manifest (try
                   (json/parse-string (:body (http/get manifest-url)) true)
                   (catch Exception e
                     (binding [*out* *err*]
                       (println "Fatal: Could not connect to the release server to download the manifest."))
                     (System/exit 1)))
        latest-version (:version manifest)
        latest-url (:url manifest)]
    (if (or (nil? latest-version) (nil? latest-url))
      (do
        (binding [*out* *err*]
          (println "Fatal: Failed to parse release manifest."))
        (System/exit 1))
      (let [latest-full-version (if-let [match (second (re-find (re-pattern ".*/antigravity-cli/([^/]+)/.*") latest-url))]
                                  match
                                  latest-version)
            current-version (current-version "antigravity-cli")
            current-base (first (str/split current-version (re-pattern "-")))]
        (println "Current installed version:" current-version)
        (println "Latest available version: " latest-full-version)
        (if (not= current-base latest-version)
          (do
            (println (str "★ A new version (" latest-version ") of antigravity-cli is available!"))
            (println (str "To update, set the version in default.nix to: " latest-full-version)))
          (println "✓ antigravity-cli is up to date."))))))
