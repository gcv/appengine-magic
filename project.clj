(defproject appengine-magic "0.3.2"
  :description "Google App Engine library for Clojure."
  :repositories {"maven-gae-plugin" "http://maven-gae-plugin.googlecode.com/svn/repository"}
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [ring/ring-core "0.3.4"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.3.7"]
                 [com.google.appengine/appengine-api-labs "1.3.7"]
                 [com.google.appengine/appengine-api-stubs "1.3.7"]
                 [com.google.appengine/appengine-local-runtime "1.3.7"]
                 [com.google.appengine/appengine-testing "1.3.7"]
                 [com.google.appengine/appengine-tools-api "1.3.7"]]
  :dev-dependencies [[swank-clojure "1.2.1"]])
