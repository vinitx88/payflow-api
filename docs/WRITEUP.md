# PayFlow – Write-Up

Backend of a UPI-style payment app

## 1. Endpoint testing (curl)

All four endpoints were tested from the terminal against the running app on `localhost:8080`. The commands and their actual JSON responses are below.

### Register two users

```bash
$ curl -X POST http://localhost:8080/users -H "Content-Type: application/json" \
  -d '{"name":"Priya Sharma","upiId":"priya@okaxis","balance":5000.0,"phoneNumber":"9876543210"}'
{"userId":1,"name":"Priya Sharma","upiId":"priya@okaxis","balance":5000.0,"phoneNumber":"9876543210"}

$ curl -X POST http://localhost:8080/users -H "Content-Type: application/json" \
  -d '{"name":"Rahul Verma","upiId":"rahul@oksbi","balance":2000.0,"phoneNumber":"9123456780"}'
{"userId":2,"name":"Rahul Verma","upiId":"rahul@oksbi","balance":2000.0,"phoneNumber":"9123456780"}
```

### List all users

```bash
$ curl http://localhost:8080/users
[{"userId":1,"name":"Priya Sharma","upiId":"priya@okaxis","balance":5000.0,"phoneNumber":"9876543210"},
 {"userId":2,"name":"Rahul Verma","upiId":"rahul@oksbi","balance":2000.0,"phoneNumber":"9123456780"}]
```

### Look up a user by id

```bash
$ curl http://localhost:8080/users/1
{"userId":1,"name":"Priya Sharma","upiId":"priya@okaxis","balance":5000.0,"phoneNumber":"9876543210"}
```

### Look up a user by UPI id

```bash
$ curl http://localhost:8080/users/upi/rahul@oksbi
{"userId":2,"name":"Rahul Verma","upiId":"rahul@oksbi","balance":2000.0,"phoneNumber":"9123456780"}
```

### Send money

```bash
$ curl -X POST http://localhost:8080/transactions -H "Content-Type: application/json" \
  -d '{"senderUpiId":"priya@okaxis","receiverUpiId":"rahul@oksbi","amount":250.0,"note":"dinner split"}'
{"transactionId":1,"senderUpiId":"priya@okaxis","receiverUpiId":"rahul@oksbi","amount":250.0,"note":"dinner split"}
```

H2 console after registering two users and one transfer:

- _[ Screenshot: `SELECT * FROM USERS` in the H2 console — paste here ]_
- _[ Screenshot: `SELECT * FROM TRANSACTIONS` in the H2 console — paste here ]_

## 2. Tables before any data (Task 2)

Open `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:payflow`, user `sa`, no password) right after startup and run the two selects before inserting anything. The columns should match the entities.

- _[ Screenshot: empty USERS table showing columns — paste here ]_
- _[ Screenshot: empty TRANSACTIONS table showing columns — paste here ]_

## 3. Demonstrating @RequestBody (Task 5.4)

`POST /users` uses `@RequestBody`, so Spring reads the JSON body and binds it onto a `User` object. I added a second handler at `POST /users/without-requestbody` that takes the same `User` parameter but WITHOUT `@RequestBody`, and printed the object inside both handlers.

### With @RequestBody

```bash
$ curl -X POST http://localhost:8080/users -H "Content-Type: application/json" \
  -d '{"name":"Priya Sharma","upiId":"priya@okaxis",...}'

// console output:
With @RequestBody -> name=Priya Sharma, upiId=priya@okaxis
```

### Without @RequestBody

```bash
$ curl -X POST "http://localhost:8080/users/without-requestbody" -H "Content-Type: application/json" \
  -d '{"name":"Ghost","upiId":"ghost@okaxis",...}'

// console output:
Without @RequestBody -> name=null, upiId=null

// HTTP response:
{"userId":null,"name":null,"upiId":null,"balance":null,"phoneNumber":null}
```

**Why are the fields null without `@RequestBody`?**

`@RequestBody` is the instruction that tells Spring to take the raw JSON in the request body and hand it to Jackson, which deserialises it into a `User` object. Without that annotation Spring does not look at the body at all — it tries to build the `User` from request parameters and method arguments instead. Since the JSON is in the body (not as query parameters) and there is nothing to populate the fields, Spring just creates a blank `User` with every field left at its default of `null`. The annotation is the bridge between the JSON payload and the Java object; remove it and the bridge is gone.

## 4. Conceptual Write-Up

### Request lifecycle

When curl sends `POST /users`, the request travels over HTTP to the embedded Tomcat server, which hands it to Spring's `DispatcherServlet` — the single front controller that receives every request. The `DispatcherServlet` looks at the URL and HTTP method and asks the Handler Mapping which controller method matches; here it finds `createUser` on `UserController`. The Handler Adapter then invokes that method, using `@RequestBody` to convert the JSON into a `User` object first. `createUser` calls the service, which saves the user, and the returned `User` is serialised back to JSON as the response.

### Serialisation

When you POST a JSON payload like `{"name":"Priya","upiId":"priya@okaxis"}`, the JSON is converted into a Java `User` object by Jackson, the JSON library Spring Boot auto-configures. Spring uses Jackson because of the `@RequestBody` annotation on the method parameter. Jackson matches each JSON key to a field (or setter) on the `User` class by name. If the JSON key were `"upi_id"` instead of `"upiId"`, Jackson would not find a matching field and would leave `upiId` as `null` — the names have to line up, or you have to tell Jackson about the alias with `@JsonProperty`.

### Spring Boot features

The three features are the embedded server, auto-configuration, and production-ready defaults. The embedded server means Tomcat is bundled in and starts on port 8080 when I run the app — no separate server install. Auto-configuration wires up the database for me: just having H2 and Spring Data JPA on the classpath gives me a `DataSource` and the H2 console without any config. Production-ready defaults provide things like the HikariCP connection pool and Jackson JSON conversion out of the box, which is what serialises my `User` responses.

### Spring vs Spring Boot

With plain Spring I would have had to set up a lot by hand: configure and deploy a Tomcat server myself, write the `DataSource` and `EntityManagerFactory` beans for the database, register the Jackson message converter for JSON, and manage all the dependency versions. Spring Boot takes care of all of that automatically — the starter dependencies pull in compatible versions, auto-configuration creates those beans for me, and the embedded server removes the deployment step. I only wrote the parts unique to PayFlow.

### Stateless REST

Stateless means the `POST /transactions` endpoint keeps no memory of any previous request — every request must carry everything the server needs to handle it. The server does not store session data between calls. This matters if PayFlow runs on three servers behind a load balancer because any server can handle any request: it does not matter which one handled your last call, since nothing about you was stored on it. That is what lets the system scale horizontally by just adding more identical servers.

### Persistence

Transactions are stored in the H2 database, not in a Java `List`. If I had used a `List`, all the transaction records would have lived only in memory, so restarting the server would wipe every record. For a payments app that is unacceptable — money transfers are the one thing that absolutely must survive a crash or restart. Persisting to a database means the records are written to durable storage and are still there after the process stops, which is exactly what a real ledger requires. (Note: H2 in-memory mode is used here for the assignment, so a real deployment would point the same JPA code at a durable database like Postgres.)
