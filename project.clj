(defproject appengine-magic "0.1.0-SNAPSHOT"
  :description "Google App Engine library for Clojure."
  :repositories {"maven-gae-plugin" "http://maven-gae-plugin.googlecode.com/svn/repository"}
  :dependencies [[org.clojure/clojure "1.1.0"]
                 [commons-codec "1.4"]
                 [commons-io "1.4"]
                 [ring/ring-core "0.2.2"]]
  ;; TODO: Some of these may not be necessary.
  :dev-dependencies [[com.google.appengine/appengine-api-1.0-sdk "1.3.3.1"]
                     [com.google.appengine/appengine-api-labs "1.3.3.1"]
                     [com.google.appengine/appengine-api-stubs "1.3.3.1"]
                     [com.google.appengine/appengine-local-runtime "1.3.3.1"]
                     [com.google.appengine/appengine-testing "1.3.3.1"]
                     [org.mortbay.jetty/jetty "6.1.21"]
                     [swank-clojure "1.2.1"]])
