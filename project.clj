(defproject appengine-magic "0.4.0-SNAPSHOT"
  :description "Google App Engine library for Clojure."
  :repositories {"releases" "https://github.com/gcv/maven-repository/raw/master/releases/"
                 "snapshots" "https://github.com/gcv/maven-repository/raw/master/snapshots/"}
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [ring/ring-core "0.3.5"]
                 [javax.servlet/servlet-api "2.5"]
                 [commons-io "1.4"]
                 [commons-codec "1.4"]
                 [commons-fileupload "1.2.1"]
                 [org.apache.commons/commons-exec "1.1"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.3.8"]
                 [com.google.appengine/appengine-api-labs "1.3.8"]
                 [com.google.appengine/appengine-api-stubs "1.3.8"]
                 [com.google.appengine/appengine-local-runtime "1.3.8"]
                 [com.google.appengine/appengine-local-runtime-shared "1.3.8"]
                 [com.google.appengine/appengine-testing "1.3.8"]
                 [com.google.appengine/appengine-tools-api "1.3.8"]]
  :dev-dependencies [[swank-clojure "1.2.1"]])
