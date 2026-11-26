# Mini-Shopify

A marketplace where merchants open a shop and stock it with products, and customers find shops by name
or category, browse their catalogues and fill a cart. SYSC 4806 term project, Carleton University.

**Live:** https://sysc4806-minishopify-ergyb4hpcef7fufw.northcentralus-01.azurewebsites.net

Every merge to `main` builds and deploys itself. The deployed database is still in memory, so the site
reseeds demo data on each deploy. See [Known gaps](#known-gaps) for why, now that #39 has landed.

---

## Current state

As of 26 November 2026, end of Sprint 2B (Milestone 2).
Status below mirrors the [project board](https://github.com/users/Drexregion/projects/2).

### Completed in Milestone 2

| # | Item | Owner | PR |
|---|------|-------|----|
| 16 | Merchant can create a shop, routed through a command handler and an event | Iheanyi | #33 |
| 38 | `Cart` and `CartItem` domain model, at most one open cart per customer | Iheanyi | #55 |
| 39 | `postgres` profile and driver so deployed data can survive restarts | Divine | #52 |
| 40 | Add to cart from the shop and item pages | Rotimi | #56 |
| 41 | Cart page: view lines, change quantity, remove | Idara | #58 |
| 44 | `ProductAddedEvent` published and projected into the catalogue read model | Iheanyi | #54 |
| 45 | Cart and checkout writes behind command handlers | Iheanyi | #57 |
| 47 | Docker Compose for Kafka and PostgreSQL | Idara | #61 |
| 48 | `Customer.transactionHistory` dropped in favour of `Transaction` | Bomi | #53 |
| | Kafka made optional behind a profile, catalogue read model added | Divine | #37 |

#37 is what unblocked the deploy. Before it, the messaging beans were created unconditionally and the
app could not start on Azure, which is the blocker Milestone 1 closed on.

### In review

| # | Item | Owner | PR |
|---|------|-------|----|
| 43 | Move search and browse onto the `ShopSummary` read model | Divine | #60 |
| 46 | Test isolation and async projection assertions | Bomi | #59 |

### Open

| # | Item | Owner |
|---|------|-------|
| 42 | Simulated checkout | Rotimi |
| 49 | Remove the stray `cqrs-fix.patch` committed onto the CQRS branch | Divine |
| 50 | This README | Divine |
| 51 | Weekly scrum, 26 November | All |
| 3 | Account page, deferred until authentication exists | Rotimi |

#42 is open even though checkout runs. The handler and the `POST /cart/checkout` endpoint arrived with
#45, so the remaining work on #42 is what that left out, which is listed under the gaps below.

### Milestone 2 use case

The brief asks for the cart. What runs end to end on the deployed site today:

1. A customer browses shops and opens a shop or item page
2. Add to cart posts a command, which is handled and redirects back with a confirmation
3. The cart page lists the lines, and the customer can change a quantity or remove a line
4. Checking out refuses an empty cart, or one holding more than is in stock, naming every short item.
   Otherwise it takes the items out of stock and closes the cart
5. The database enforces at most one open cart per customer through a unique column, not through
   application code that could race

### Numbers

- 221 tests across 29 test classes, most driving real HTTP against a real database
- 25 pull requests merged, each behind two approving reviews and a green build
- 2 pull requests open (#59, #60)
- 0 direct commits to `main` by a team member, everything arrived through a pull request

---

## Plan for the rest of Milestone 2

Due 26 November. Ordered by what blocks what.

| Priority | Item | Why now | Owner |
|---|------|---------|-------|
| 1 | Land #59, test isolation | Every other branch rebases onto it, so the longer it waits the more conflicts it causes | Bomi |
| 2 | Land #60, browse and search on the read model | Conflicts with #59 in five test classes, so whichever lands second resolves | Divine |
| 3 | Finish #42: write a `Transaction` on checkout | Without it a purchase leaves no record, which the schema says is `TRANSACTION`'s whole job | Rotimi |
| 4 | Provision the Azure Postgres server and set the connection variables | #39 shipped the profile but production is not using it | Divine |
| 5 | #49, delete the stray patch file | Housekeeping, already gone from `main` | Divine |

## Known gaps

- **Checkout records no purchase.** `CheckoutCommandHandler` takes the stock out and closes the cart,
  but writes no row to `TRANSACTION`. That table is the only record of a purchase, which is what #48
  established, so a customer can check out and their purchase history stays empty. `Transaction` also
  carries no quantity or price paid. This is the rest of #42.
- **The deployed site still runs in-memory H2.** #39 merged `application-postgres.properties` and the
  PostgreSQL driver, but no Azure database has been provisioned and the deploy workflow sets no
  `SPRING_PROFILES_ACTIVE`. Production therefore falls back to `application.properties`, which is
  `ddl-auto=create-drop`, and data still resets on every deploy. The issue is closed because the code
  landed; the infrastructure half is item 4 above.
- **Two browse paths, until #60 lands.** `/catalogue` reads the `ShopSummary` read model and never
  touches `ShopRepository`. `/shops` and `/shops/search` still read the write model directly on `main`.
- **Checkout publishes no event.** Every other write does. The read model therefore never hears that
  stock moved. Nothing visibly breaks, because the catalogue does not carry stock, but it is the one
  silent write in the system.
- **Nothing reserves stock.** Adding to a cart checks the stock on hand but does not hold it, and two
  checkouts running at once can both pass the check and both deduct. A `@Version` on `Item`, or a
  pessimistic read, would close it.
- **No authentication.** `LandingController` and `CurrentCustomer` stand in the first seller and the
  first customer in the database. Any visitor acts as those people.
- **No broker in production.** Azure App Service free tier cannot host Kafka. The deployed build runs
  with messaging disabled, so events are applied in process. The Kafka path is demonstrated locally
  with `docker-compose.yml` and the `kafka` profile, and in CI by #59's embedded broker.

---

## Database schema

Generated by Hibernate from the `@Entity` classes at startup (`ddl-auto=create-drop` locally and in
production, `update` under the `postgres` profile). Both inheritance hierarchies use the JPA default,
single-table-per-hierarchy with a `DTYPE` discriminator column.

### Write model

#### `PERSON`, from Person, Customer, Seller

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `dtype` | varchar | discriminator: `Person`, `Customer`, `Seller` |
| `name` | varchar | |
| `email` | varchar | |
| `password` | varchar | plain text today, replaced when authentication lands |
| `phone` | varchar | |
| `address` | varchar | Customer only, null for other subtypes |

#### `PRODUCT`, from Product, Item, Service

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `dtype` | varchar | discriminator: `Product`, `Item`, `Service` |
| `name` | varchar | |
| `description` | varchar | |
| `price` | double | |
| `quantity` | integer | Item only, inventory count |
| `product_type` | varchar | Item only |
| `duration` | integer | Service only, minutes |

#### `SHOP`

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `name` | varchar | |
| `description` | varchar | |
| `type` | varchar | the shop's category, what search matches on |
| `seller_id` | bigint | FK to `PERSON.id` |

#### `TRANSACTION`

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `date` | timestamp | set when the purchase is made |
| `shop_id` | bigint | FK → `SHOP.id` |
| `product_id` | bigint | FK → `PRODUCT.id` |
| `customer_id` | bigint | FK → `PERSON.id` |
| `cart_id` | bigint | the checked-out cart the purchase came from, so one order's rows can be found together; null for rows not written by checkout |
| `quantity` | integer | how many were bought; nullable so the column can be added to an existing table (#42) |
| `unit_price` | double | the price paid for one at checkout, kept even if the product's price changes later |

Checkout writes one row per cart line (#42). `TRANSACTION` is the only record of a purchase. A customer's purchase history is
`TransactionRepository.findByCustomerOrderByDateDesc(customer)`; `Customer` stores no copy of it (#48).
### `CART`

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `customer_id` | bigint | FK to `PERSON.id`, not null |
| `status` | varchar | `OPEN` or `CHECKED_OUT` |
| `open_for_customer_id` | bigint | unique, holds the customer's id while the cart is open and null once checked out. This is what enforces at most one open cart per customer |

#### `CART_ITEM`

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `cart_id` | bigint | FK to `CART.id`, not null |
| `product_id` | bigint | FK to `PRODUCT.id`, not null |
| `quantity` | integer | always greater than zero |

#### Join tables

| Table | Columns | From |
|---|---|---|
| `SHOP_PRODUCTS` | `shop_id`, `products_id` | `Shop.products`, unidirectional `@OneToMany` |
| `SHOP_CUSTOMERS` | `shop_id`, `customers_id` | `Shop.customers`, `@ManyToMany` |
| `PERSON_FAVORITE_SHOPS` | `customer_id`, `favorite_shops_id` | `Customer.favoriteShops`, `@ManyToMany`, named after `PERSON`, the table Customer shares |

`Seller.shops` is mapped by `Shop.seller` and creates no table of its own.

### Read model

Derived entirely from events. Nothing writes to these tables except `ShopSummaryProjection`, and they
carry no foreign keys back into the write model on purpose: the read side can be dropped and rebuilt by
replaying the log.

#### `SHOP_SUMMARY`

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key, generated |
| `shop_id` | bigint | the id of the shop this row was built from. Deliberately not a foreign key |
| `seller_id` | bigint | copied off the event |
| `name` | varchar | null until the shop-created event is applied, which is how a half built row is spotted |
| `description` | varchar | |
| `type` | varchar | |

#### `SHOP_SUMMARY_PRODUCT`

The collection table behind `ShopSummary.products`, a map of product id to kind.

| Column | Type | Notes |
|---|---|---|
| `shop_summary_id` | bigint | FK to `SHOP_SUMMARY.id` |
| `product_id` | bigint | map key. Keeping ids rather than counters is what makes product events idempotent |
| `kind` | varchar | `ITEM` or `SERVICE` |

Browse the live schema at `/h2-console` when running locally
(JDBC URL `jdbc:h2:mem:minishopify`, user `sa`, no password).

### Relationships

```
Person ─┬─< Customer
        └─< Seller ──1:N── Shop ──M:N── Product        (via SHOP_PRODUCTS)
                            │
                            ├──M:N── Customer           (via SHOP_CUSTOMERS)
                            │
                            └──1:N── Transaction ──N:1── Customer
                                          │
                                          └──N:1── Product

Customer ──1:N── Cart ──1:N── CartItem ──N:1── Product   (at most one OPEN cart per customer)

Product ─┬─< Item      (quantity, productType)
         └─< Service   (duration)
```

The read model sits beside all of this rather than inside it:

```
CreateShopCommand ─> CreateShopCommandHandler ─> Shop        (write model)
                                   │
                                   └─> ShopCreatedEvent ─┐
AddProductCommand ─> AddProductCommandHandler ─> Product   │
                                   │                       │
                                   └─> ProductAddedEvent ─┤
                                                           ▼
                                             ShopSummaryProjection
                                                           │
                                                           ▼
                                        SHOP_SUMMARY + SHOP_SUMMARY_PRODUCT
                                                           │
                                                           ▼
                                               CatalogueController  (/catalogue)
```

Which listener applies the event depends on the profile. Under `kafka`, `ShopCreatedListener` and
`ProductAddedListener` read it off the broker. Without the profile those two beans do not exist, so
nothing consumes the events at all and only the startup backfill writes to the read model. #60 adds
`InProcessProjectionListener`, profiled `!kafka` as the exact complement of the other two, which takes
the event straight from Spring's publisher instead. An event is then applied once either way, and the
projection is idempotent regardless.

---

## Running it

### Requirements

- JDK 21
- Maven 3.9+
- Docker, only for Kafka and Postgres
### Locally

```bash
mvn spring-boot:run
```

Then http://localhost:8080. Seed data loads automatically when the shop table is empty.

### With Kafka and Postgres

`docker-compose.yml` runs every dependency the app can use: a Kafka broker and a PostgreSQL database.

```bash
docker compose up -d
```

Then start the app against them:

```bash
DB_URL=jdbc:postgresql://localhost:5433/minishopify DB_USER=minishopify DB_PASSWORD=minishopify \
  mvn spring-boot:run -Dspring-boot.run.profiles=kafka,postgres
```

Postgres is published on port 5433 so it does not clash with a Postgres already installed on 5432.
The database login is for local use only and is not the one Azure uses. Data is kept in the
`postgres-data` volume, so it survives restarts. To stop everything:

```bash
docker compose down
```

Add `-v` to also delete the stored data. Either profile can be used alone: `kafka` on its own keeps
H2, and `postgres` on its own runs with no messaging.

Without the `kafka` profile the messaging beans are not created and the application runs with no broker,
which is how it runs on Azure. Shops are still created, the event is simply not published. With no
profiles at all the app needs nothing from compose.

### Tests

```bash
mvn verify
```
 
---

## Architecture

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.1 |
| Web | Spring MVC, Thymeleaf, post-redirect-get |
| Persistence | Spring Data JPA, Hibernate |
| Database | H2 in memory, PostgreSQL ready behind the `postgres` profile |
| REST | Spring Data REST, base path `/api`. The read model repository is not exported |
| Write side | Commands and handlers under `ca.carletonu.cqrs` |
| Read side | `ShopSummary` projection, rebuilt from events |
| Messaging | Spring for Apache Kafka, profile `kafka`, optional at runtime |
| Build | Maven |
| CI | GitHub Actions, `mvn verify` on every push and pull request |
| Deployment | Azure App Service, Linux, OIDC federated identity |

### Packages

```
ca.carletonu
├── controllers      Landing, Shop, ShopSearch, ShopProduct, Product, Seller, Cart,
│                    Catalogue, CurrentCustomer
├── models           Person, Customer, Seller, Shop, Product, Item, Service,
│   │                Transaction, Cart, CartItem, CartStatus
│   └── repositories Spring Data repositories
├── cqrs
│   ├── commands     CreateShop, AddProduct, AddToCart, ChangeCartQuantity,
│   │                RemoveFromCart, Checkout
│   └── handlers     one handler per command, plus CheckoutResult and the
│                    not-found exceptions
├── events           ShopCreatedEvent, ProductAddedEvent, CartItemAddedEvent, ProductKind
├── messaging        KafkaProducer, KafkaConsumer, KafkaTopicConfig
├── readmodel        ShopSummary, ShopSummaryProjection, ShopSummaryRepository,
│                    ShopCreatedListener, ProductAddedListener (profile kafka),
│                    InProcessProjectionListener (profile !kafka, arrives with #60)
└── data             DemoDataSeeder
```

---

## How we work

Set out in full in [CONTRIBUTING.md](CONTRIBUTING.md).

- `main` is protected. No direct pushes
- Branches are `feat/`, `fix/`, `ci/` or `docs/`
- Every pull request needs two approving reviews and a green CI build
- Pull requests close their issue with a keyword in the **description**. A keyword in the title does
  not work, which cost us a stale board card once already
- Weekly scrum notes are kept as issues

## Team

| | Area |
|---|---|
| Divine Eyo | CI/CD, Azure deployment, read model, project board and issue hygiene |
| Iheanyi Njokuobi | Domain model, CQRS command side, events |
| Idara-Abasi Udoh | Views and templates, cart page |
| Rotimi Ajayi | Controllers, search, product creation, add to cart |
| Olasubomi Akingbade | Testing, item details page, schema fixes |

Everyone authors pull requests and reviews other people's across all areas.
