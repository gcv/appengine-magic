## 0.3.0 (???)

* Added `appengine-magic.core/appengine-environment-type`.
* Deprecated including the `:aot` directive in `project.clj` files.
* Added `exists?` function to the Datastore service.
* Added the Task Queues service.
* Added the Mail service.
* Added the Blobstore service.
* Speeded up production deployment by packaging compiled application .class
  files into a .jar. Eliminated the WEB-INF/classes/ directory.
* Added support for general classpath resources with the
  `appengine-magic.core/open-resource-stream` function.
* Breaking change: moved the web application from `resources/war/` to
  `resources/`. This provides better support for using general classpath
  resources.
* Breaking change: renamed all destructive Memcache service operations to have a
  trailing ! character; e.g., `appengine-magic.services/put` became
  `appengine-magic.services/put!`.


## 0.2.1 (2010-10-20)

* Improved the User service.
* Fixed a bug with project paths containing spaces.
* Added the URL Fetch service (thanks to Brian Gruber).
* Added testing infrastructure.
