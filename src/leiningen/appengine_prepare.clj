(ns leiningen.appengine-prepare
  "Prepares a the Google App Engine application for deployment."
  (:use appengine-magic.utils)
  (:require leiningen.compile lancet)
  (:import java.io.File))


(defn appengine-prepare [project]
  (println "preparing App Engine application" (:appengine-display-name project)
           "for deployment")
  (let [classes-dir *compile-path*
        resources-dir (File. (:resources-path project))
        lib-dir (File. (:library-path project))
        lib-dev-dir (File. lib-dir "dev")
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")
        target-classes-dir (File. WEB-INF-dir "classes")
        target-lib-dir (File. WEB-INF-dir "lib")
        prj-application (:appengine-application project)]
    ;; compile all
    (leiningen.compile/compile project)
    ;; delete existing content of target classes/ and lib/
    (lancet/delete {:dir (.getPath target-classes-dir)})
    (lancet/delete {:dir (.getPath target-lib-dir)})
    ;; copy the compiled app itself
    (lancet/mkdir {:dir target-classes-dir})
    (lancet/mkdir {:dir target-lib-dir})
    (lancet/copy {:todir (.getPath target-classes-dir)}
                 (lancet/fileset
                  {:dir *compile-path*
                   :includes (str (dash_ prj-application) "/**")}))
    ;; copy important dependencies into WEB-INF/lib
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset
                  {:dir lib-dir
                   :includes "*"
                   :excludes "dev,appengine*,servlet-api*,swank-clojure*,jetty*"}))
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset
                  {:dir lib-dev-dir
                   :includes "appengine-magic*,ring-core*,ring-servlet*"}))))
