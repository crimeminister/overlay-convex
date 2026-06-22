(ns overlay.system-test
  "Unit tests for the overlay.system namespace."
  (:require
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is testing]]
   [overlay.system :as sys]))

(deftest test-sh-output
  (testing "Returns trimmed stdout on success"
    (with-redefs [shell/sh (fn [& _args]
                             {:exit 0 :out "   hello world \n" :err ""})]
      (is (= "hello world" (sys/sh-output "echo" "hello")))))

  (testing "Throws exception on non-zero exit code"
    (with-redefs [shell/sh (fn [& _args]
                             {:exit 1 :out "" :err "some error"})]
      (is (thrown-with-msg? Exception #"Command failed" (sys/sh-output "false"))))))

(deftest test-os
  (testing "Detects Darwin as darwin"
    (with-redefs [sys/sh-output (fn [& _args] "Darwin")]
      (is (= "darwin" (sys/os)))))

  (testing "Detects Linux as linux"
    (with-redefs [sys/sh-output (fn [& _args] "Linux")]
      (is (= "linux" (sys/os))))))

(deftest test-arch
  (testing "Detects x86_64 as amd64"
    (with-redefs [sys/sh-output (fn [& _args] "x86_64")]
      (is (= "amd64" (sys/arch)))))

  (testing "Detects arm64 as arm64"
    (with-redefs [sys/sh-output (fn [& _args] "arm64")]
      (is (= "arm64" (sys/arch))))))

(deftest test-musl
  (testing "Returns true if shell command output contains musl"
    (with-redefs [shell/sh (fn [& _args]
                             {:exit 0 :out "ldd (musl-libc) 1.2.3\n" :err ""})]
      (is (true? (sys/musl?)))))

  (testing "Returns false if shell command output does not contain musl"
    (with-redefs [shell/sh (fn [& _args]
                             {:exit 0 :out "ldd (GNU libc) 2.31\n" :err ""})]
      (is (false? (sys/musl?))))))

(deftest test-platform
  (testing "macOS platform string format"
    (with-redefs [sys/os (fn [] "darwin")
                  sys/arch (fn [] "arm64")]
      (is (= "darwin_arm64" (sys/platform)))))

  (testing "Linux glibc platform string format"
    (with-redefs [sys/os (fn [] "linux")
                  sys/arch (fn [] "amd64")
                  sys/musl? (fn [] false)]
      (is (= "linux_amd64" (sys/platform)))))

  (testing "Linux musl platform string format"
    (with-redefs [sys/os (fn [] "linux")
                  sys/arch (fn [] "amd64")
                  sys/musl? (fn [] true)]
      (is (= "linux_amd64_musl" (sys/platform))))))
