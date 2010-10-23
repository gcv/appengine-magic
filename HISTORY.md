## 0.3.0 (???)

* Support for general classpath resources with the
  `appengine-magic/open-resource-stream` function.
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
