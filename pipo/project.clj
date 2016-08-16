(defproject pipo/pipo "0.0.1-SNAPSHOT"
  :description "FIXME: Android project description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :plugins [[lein-droid "0.4.3"]
            [cider/cider-nrepl "0.8.2"]]

  :dependencies [[org.clojure-android/clojure "1.7.0-r4" :use-resources true]
                 [neko/neko "4.0.0-alpha5"]
                 [net.danlew/android.joda "2.9.2" :extension "aar"]
                 [clj-time "0.11.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 ]
  :profiles {:default [:dev]

             :repl
             [{:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}]

             :local-testing
             [:repl
              {:android {:aot :all-with-unused}
               :target-path "target/local-testing"
               :dependencies [[org.robolectric/robolectric "3.0"]
                              [org.clojure-android/droid-test "0.1.1-SNAPSHOT"]]}]

             :dev
             [:repl
              {:target-path "target/debug"
               :android {:aot :all-with-unused
                         :aot-exclude-ns [#"org\.pipo\.t-.+"]
                         :rename-manifest-package "org.pipo.debug"
                         :manifest-options {:app-name "pipo - debug"}}}]

             :release
             [{:target-path "target/release"
               :android
               { ;; Specify the path to your private keystore
                ;; and the the alias of the key you want to
                ;; sign APKs with.
                ;; :keystore-path "/home/user/.android/private.keystore"
                ;; :key-alias "mykeyalias"

                :ignore-log-priority [:debug :verbose]
                :aot :all
                :build-type :release}}]

             :lean
             [:release
              {:dependencies ^:replace [[org.skummet/clojure "1.7.0-r2"]
                                        [neko/neko "4.0.0-alpha5"]
                                        [net.danlew/android.joda "2.9.2" :extension "aar"]
                                        [clj-time "0.11.0"]
                                        [org.clojure/java.jdbc "0.4.2"]
                                        ]
               :exclusions [[org.clojure/clojure]
                            [org.clojure-android/clojure]]
               :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
               :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
               :android {:lean-compile true
                         ; :proguard-execute true
                         ; :proguard-conf-path "build/proguard-minify.cfg"
                         }}]
             }

  :android {;; Specify the path to the Android SDK directory.
            ;; :sdk-path "/home/user/path/to/android-sdk/"
            :sdk-path "../sdk/android-sdk-linux"

            ;; Try increasing this value if dexer fails with
            ;; OutOfMemoryException. Set the value according to your
            ;; available RAM.
            :dex-opts ["-JXmx4096M" "--incremental"]

            ;; If previous option didn't work, uncomment this as well.
            ;; :force-dex-optimize true

            :target-version "22"
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"
                             "cljs-tooling.complete" "cljs-tooling.info"
                             "cljs-tooling.util.analysis" "cljs-tooling.util.misc"
                             "cider.nrepl" "cider-nrepl.plugin"
                             "cider.nrepl.middleware.util.java.parser"]})
