(ns overlay.version-test
  "Unit tests for the overlay.version Babashka library."
  (:require
   [babashka.http-client :as http]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [overlay.version :as v]))

(deftest test-parse-version-fallback
  (testing "Successfully parsing version from default.nix file format"
    (let [temp-file (java.io.File/createTempFile "default_test" ".nix")]
      (spit temp-file (str "self: super:\n"
                           "{\n"
                           "  my-package = self.callPackage ./pkgs/my-package {\n"
                           "    version = \"1.2.3\";\n"
                           "    hash = \"sha256-abc\";\n"
                           "  };\n"
                           "}\n"))
      (try
        (is (= "1.2.3" (v/parse-version-fallback (.getAbsolutePath temp-file) "my-package")))
        (finally
          (.delete temp-file)))))

  (testing "Throwing exception when package name is not found"
    (let [temp-file (java.io.File/createTempFile "default_test" ".nix")]
      (spit temp-file "self: super: { }")
      (try
        (is (thrown? Exception (v/parse-version-fallback (.getAbsolutePath temp-file) "missing-package")))
        (finally
          (.delete temp-file))))))

(deftest test-current-property
  (testing "Succeeds via nix-instantiate mock"
    (with-redefs [shell/sh (fn [_cmd & _args]
                             {:exit 0 :out "\"some-val\"\n" :err ""})]
      (is (= "some-val" (v/current-property "some-package" "rev")))))

  (testing "Falls back to manual parsing on nix-instantiate failure"
    (let [temp-file (java.io.File/createTempFile "default_test" ".nix")]
      (spit temp-file (str "{\n"
                           "  my-pkg = self.callPackage ./pkgs/my-pkg {\n"
                           "    rev = \"some-sha\";\n"
                           "  };\n"
                           "}\n"))
      (try
        (with-redefs [shell/sh (fn [_cmd & _args]
                                 {:exit 1 :out "" :err "nix-instantiate failed"})]
          (is (= "some-sha" (v/current-property (.getAbsolutePath temp-file) "my-pkg" "rev"))))
        (finally
          (.delete temp-file))))))

(deftest test-current-version
  (testing "Succeeds via nix-instantiate mock"
    (with-redefs [shell/sh (fn [_cmd & _args]
                             {:exit 0 :out "\"2.3.4\"\n" :err ""})]
      (is (= "2.3.4" (v/current-version "some-package")))))

  (testing "Falls back to parse-version-fallback on nix-instantiate failure"
    (let [temp-file (java.io.File/createTempFile "default_test" ".nix")]
      (spit temp-file (str "{\n"
                           "  my-pkg = self.callPackage ./pkgs/my-pkg {\n"
                           "    version = \"4.5.6\";\n"
                           "  };\n"
                           "}\n"))
      (try
        (with-redefs [shell/sh (fn [_cmd & _args]
                                 {:exit 1 :out "" :err "nix-instantiate failed"})]
          (is (= "4.5.6" (v/current-version (.getAbsolutePath temp-file) "my-pkg"))))
        (finally
          (.delete temp-file))))))

(deftest test-fetch-github-latest-version
  (testing "Fetching latest release successfully"
    (with-redefs [http/get (fn [url _opts]
                             (is (str/includes? url "/releases/latest"))
                             {:status 200 :body "{\"tag_name\": \"v1.5.0\"}"})]
      (is (= "1.5.0" (v/fetch-github-latest-version "owner/repo" {:strategy :release :strip-v? true})))))

  (testing "Fetching latest release without stripping v"
    (with-redefs [http/get (fn [_url _opts]
                             {:status 200 :body "{\"tag_name\": \"v1.5.0\"}"})]
      (is (= "v1.5.0" (v/fetch-github-latest-version "owner/repo" {:strategy :release :strip-v? false})))))

  (testing "Fetching latest tags successfully"
    (with-redefs [http/get (fn [url _opts]
                             (is (str/includes? url "/tags"))
                             {:status 200 :body "[{\"name\": \"v2.1.0-alpha\"}, {\"name\": \"v2.0.0\"}]"})]
      (is (= "2.1.0-alpha" (v/fetch-github-latest-version "owner/repo" {:strategy :tag :strip-v? true})))))

  (testing "Fetching latest commit successfully"
    (with-redefs [http/get (fn [url _opts]
                             (is (str/includes? url "/commits/master"))
                             {:status 200 :body "{\"sha\": \"abcdef1234567890\"}"})]
      (is (= "abcdef1234567890" (v/fetch-github-latest-version "owner/repo" {:strategy :commit})))))

  (testing "HTTP error throws exception"
    (with-redefs [http/get (fn [_url _opts]
                             {:status 404 :body "Not Found"})]
      (is (thrown? Exception (v/fetch-github-latest-version "owner/repo"))))))

(deftest test-report-update-status
  (testing "Package is up to date"
    (let [output (with-out-str
                   (is (false? (v/report-update-status "test-pkg" "1.0.0" "1.0.0"))))]
      (is (str/includes? output "Current installed version of test-pkg : 1.0.0"))
      (is (str/includes? output "Latest available version of test-pkg : 1.0.0"))
      (is (str/includes? output "✓ test-pkg is up to date."))))

  (testing "Package needs update (default instruction)"
    (let [output (with-out-str
                   (is (true? (v/report-update-status "test-pkg" "1.0.0" "1.1.0"))))]
      (is (str/includes? output "Current installed version of test-pkg : 1.0.0"))
      (is (str/includes? output "Latest available version of test-pkg : 1.1.0"))
      (is (str/includes? output "★ A new version (1.1.0) of test-pkg is available!"))
      (is (str/includes? output "nix-shell -p common-updater-scripts --run \"update-source-version test-pkg 1.1.0\""))))

  (testing "Package needs update (custom instruction)"
    (let [opts {:update-instruction-fn (fn [pkg ver] (str "Custom update instruction for " pkg " to version " ver))}
          output (with-out-str
                   (is (true? (v/report-update-status "test-pkg" "1.0.0" "1.1.0" opts))))]
      (is (str/includes? output "Custom update instruction for test-pkg to version 1.1.0")))))

(deftest test-check-github-commit-update
  (testing "Commit is up to date"
    (with-redefs [v/current-property (fn [_pkg _prop] "abcdef1234567890")
                  v/fetch-github-latest-version (fn [_repo _opts] "abcdef1234567890")]
      (let [output (with-out-str
                     (is (false? (v/check-github-commit-update "test-pkg" "owner/repo"))))]
        (is (str/includes? output "Current installed rev of test-pkg : abcdef1234567890"))
        (is (str/includes? output "Latest available rev of test-pkg : abcdef1234567890"))
        (is (str/includes? output "✓ test-pkg is up to date.")))))

  (testing "Commit needs update (default instruction)"
    (with-redefs [v/current-property (fn [_pkg _prop] "old-sha-1234567890")
                  v/fetch-github-latest-version (fn [_repo _opts] "new-sha-1234567890")]
      (let [output (with-out-str
                     (is (true? (v/check-github-commit-update "test-pkg" "owner/repo"))))]
        (is (str/includes? output "Current installed rev of test-pkg : old-sha-1234567890"))
        (is (str/includes? output "Latest available rev of test-pkg : new-sha-1234567890"))
        (is (str/includes? output "★ A new commit (new-sha) of test-pkg is available!"))
        (is (str/includes? output "To update, update the 'rev' attribute of test-pkg in default.nix to: new-sha-1234567890")))))

  (testing "Commit needs update (custom instruction)"
    (with-redefs [v/current-property (fn [_pkg _prop] "old-sha-1234567890")
                  v/fetch-github-latest-version (fn [_repo _opts] "new-sha-1234567890")]
      (let [opts {:update-instruction-fn (fn [pkg rev] (str "Custom update instruction for " pkg " to rev " rev))}
            output (with-out-str
                     (is (true? (v/check-github-commit-update "test-pkg" "owner/repo" opts))))]
        (is (str/includes? output "Custom update instruction for test-pkg to rev new-sha-1234567890"))))))
