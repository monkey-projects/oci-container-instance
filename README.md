# OCI Container Instances

This is a Clojure library that allows you to access the [Oracle Container
Instance API](https://docs.oracle.com/en-us/iaas/Content/container-instances/home.htm)
in an idiomatic fashion.  There already exists a Java library, provided
by Oracle itself, but I think it's not very efficient to first call a
bunch of Java classes, marshalling all the JSON stuff into POJOs and then
converting them back to Clojure maps.  This library uses [Martian](https://github.com/oliyh/martian)
instead, to directly call the REST API.

## Usage

First include the library in your project:
```clojure
# deps.edn
{com.monkeyprojects/oci-container-instance {:mvn/version "<version>"}}
```
Or
```clojure
# Leiningen
[com.monkeyprojects/oci-container-instance "<version>"]
```

Include the namespace, and create a client using your OCI credentials:
```clojure
(require '[monkey.oci.container-instance.core :as ci])

;; Create a Martian client (aka context)
(def ctx (ci/make-context {:tenancy-ocid "my-tenancy-ocid"
                           :user-ocid "my-user-ocid"
			   :key-fingerprint "fingerprint"
			   :private-key my-private-key
			   :region "oci-region"}))

;; Start sending requests
(ci/create-container-instance ctx {:container-instance {...}})
```

See the [API documentation](https://docs.oracle.com/en-us/iaas/api/#/en/container-instances/20210415/)
to see which calls are available and which body parameters you need to pass in.
For some calls you will need to specify the `:compartment-id` which is the
OCID of the compartment.  For others you only need the `:instance-id`.
Sometimes you also need to pass in a body, for example when creating an new
container instance.

Each request returns a `future` to the result, because in the background
we are using [http-kit](https://github.com/http-kit/http-kit).  It emphasises
the remote nature of the functions and also allows you to implement an async
flow using [Manifold](https://github.com/clj-commons/manifold).

## License

Copyright (c) 2023 by [Monkey Projects](https://www.monkey-projects.be).
Licensed under [MIT license](LICENSE).