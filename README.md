
# apr-24-nic-change-calculator

This service stores the anonymised results of calculations performed by the related frontend microservice `apr-24-nic-change-calculator-frontend`, and exposes an endpoint used by `apr-24-nic-change-calculator-admin-frontend` to expose summary data (number of calculations, total potential savings etc.).

## How to run the service

You can run the service locally with `sbt run`.  The service will run on port `11404`.

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").