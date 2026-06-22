(ns overlay.system
  "System and platform detection utility functions."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

(defn sh-output
  "Runs a command and returns its trimmed stdout, or throws if exit code is non-zero."
  [& args]
  (let [{:keys [exit out err]} (apply shell/sh args)]
    (if (zero? exit)
      (str/trim out)
      (throw (ex-info (str "Command failed: " (str/join " " args)) {:err err})))))

(defn os
  "Returns the OS string ('darwin' or 'linux'). Exits on unsupported operating systems."
  []
  (let [s (sh-output "uname" "-s")]
    (cond
      (= s "Darwin") "darwin"
      (= s "Linux") "linux"
      :else (do (binding [*out* *err*]
                  (println (str "Fatal: Unsupported operating system: " s)))
                (System/exit 1)))))

(defn arch
  "Returns the architecture string ('amd64' or 'arm64'). Exits on unsupported architectures."
  []
  (let [m (sh-output "uname" "-m")]
    (cond
      (or (= m "x86_64") (= m "amd64")) "amd64"
      (or (= m "arm64") (= m "aarch64")) "arm64"
      :else (do (binding [*out* *err*]
                  (println (str "Fatal: Unsupported architecture: " m)))
                (System/exit 1)))))

(defn musl?
  "Returns true if the current system is running musl libc, false otherwise."
  []
  (or (.exists (java.io.File. "/lib/libc.musl-x86_64.so.1"))
      (.exists (java.io.File. "/lib/libc.musl-aarch64.so.1"))
      (try
        (let [{:keys [exit out]} (shell/sh "sh" "-c" "ldd /bin/ls 2>&1")]
          (and (= exit 0) (str/includes? out "musl")))
        (catch Exception _ false))))

(defn platform
  "Returns the platform string (e.g., 'linux_amd64', 'linux_amd64_musl', 'darwin_arm64')."
  []
  (let [current-os (os)
        current-arch (arch)]
    (if (= current-os "linux")
      (if (musl?)
        (str "linux_" current-arch "_musl")
        (str "linux_" current-arch))
      (str current-os "_" current-arch))))
