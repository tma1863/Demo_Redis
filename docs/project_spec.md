# Project Specification: E-Commerce Catalog Performance Optimization with Redis Cache

## I. PROJECT OBJECTIVES

### Core Objective
Demonstrate and quantitatively measure the performance differences—specifically response time, throughput, and hardware resource consumption—between a traditional backend system (relational database only) and a cache-optimized system utilizing Redis Cache via Spring Cache Abstraction.

### Personal Technical Objectives
* Master the setup of an isolated development environment using Docker (PostgreSQL & Redis) on Windows.
* Understand and correctly apply core Spring Cache annotations: `@Cacheable`, `@CachePut`, and `@CacheEvict`.
* Utilize performance testing tools (Benchmark Tools) to read, analyze, and interpret system metrics.

---

## II. PROJECT SCOPE

### Business Module
To keep the scope minimal, the project focuses exclusively on **Catalog Management** (Category Browsing & Product Lookup) within an E-Commerce system. Authentication (Auth), Shopping Cart (Cart), and Checkout features are explicitly excluded.

### Tech Stack
* **Backend Framework:** Java Spring Boot 3.x/4.x, Spring Data JPA, Spring Cache.
* **Primary Database (Single Source of Truth):** PostgreSQL 17 (Running in a Docker container).
* **Cache Server:** Redis 8.x (Running in a Docker container).
* **Benchmarking Tools:** Apache JMeter or Bombardier (Running locally on Windows).

---

## III. DATABASE SCHEMA & SEEDING STRATEGY

### Data Models

#### 1. `categories` Table
* `id` (BIGINT, Primary Key, Auto Increment)
* `name` (VARCHAR, Not Null)
* `slug` (VARCHAR, Unique)
* *Role:* Supports the product browsing by category feature.

#### 2. `products` Table
* `id` (BIGINT, Primary Key, Auto Increment)
* `category_id` (BIGINT, Foreign Key)
* `name` (VARCHAR, Not Null)
* `price` (NUMERIC, Not Null)
* `stock_quantity` (INT, Not Null)
* `description` (TEXT)
* *Role:* The primary target for Read/Write/Update cache operations.

#### 3. `order_items` Table (Supplemental table for Heavy Queries)
* `id` (BIGINT, Primary Key, Auto Increment)
* `product_id` (BIGINT, Foreign Key)
* `quantity` (INT, Not Null) - *The quantity of products purchased in a single order item.*
* *Role:* Simulates historical purchase data to trigger heavy database `JOIN` and `GROUP BY` operations.

### Seeding Strategy
The system will be pre-populated with a large volume of mock data (Seed Data) to expose hardware latency and performance bottlenecks:
* **Categories:** 20 records
* **Products:** 1,000 records
* **Order Items:** Minimum 50,000 records

---

## IV. FEATURES & TECHNICAL RATIONALE

The project targets three "battleground" scenarios representing the most critical bottlenecks in backend performance, plus an essential data synchronization mechanism.

### 1. Trending Products Leaderboard (`GET /api/products/trending`)
* **Description:** Scans the `order_items` table, performs a `JOIN` with the `products` table, groups the data by product, calculates the total quantity sold, sorts the results in descending order, and fetches the top 10 products.
* **Rationale (Solving Heavy Queries):**
    * **Without Cache:** The system forces PostgreSQL to recompute the aggregation algorithm across 50,000+ rows for every single request. Disks are continuously read, stretching response times to $1000\text{ms} - 3000\text{ms}$.
    * **With Cache (`@Cacheable`):** The top 10 results from the initial execution are captured as a lightweight JSON string and stored in Redis RAM. Subsequent requests fetch data from RAM in just $2\text{ms} - 5\text{ms}$, completely offloading processing from the PostgreSQL CPU.

### 2. Advanced Product Filter (`GET /api/products/search`)
* **Description:** Performs dynamic product lookups based on optional client-side filters: price range (`price BETWEEN`), category selection (`category_id`), and partial text search (`LIKE %keyword%`).
* **Rationale (Solving Dynamic Queries & Index Evasion):**
    * **Without Cache:** Text searches using `LIKE '%...%'` invalidate standard database indexes, forcing PostgreSQL into a full table scan. Additionally, frequently shifting query parameters prevent the DB from reusing execution plans effectively.
    * **With Cache (`@Cacheable`):** Spring Cache hashes the combination of all incoming search parameters into a unique Redis key. If another user performs a search with identical criteria, the results are delivered instantly from RAM without hitting the disk.

### 3. High-Concurrency Spike Simulation (`GET /api/products/{id}`)
* **Description:** Fetches the details of a specific product. This scenario uses Bombardier or JMeter to bombard a single "hot" product endpoint with 1,000 concurrent requests simultaneously.
* **Rationale (Solving High Concurrency & Protecting the Database):**
    * **Without Cache:** 1,000 simultaneous connections overwhelm the backend. Since the PostgreSQL connection pool is typically limited (defaulting to 10–100 connections), late-arriving requests are forced to queue. If they exceed the timeout threshold, the application throws a `ConnectionTimeoutException`, leading to service downtime.
    * **With Cache (`@Cacheable`):** Redis operates on a single-threaded, non-blocking architecture entirely in RAM, effortlessly handling hundreds of thousands of requests per second. The subsequent 999 requests are served instantly from the Redis cache layer, shielding PostgreSQL from the traffic spike.

### 4. Supplemental Feature: Product Update (`PUT /api/products/{id}`)
* **Description:** Updates core product details (Name, Price).
* **Rationale (Data Synchronization - Cache Invalidation):**
    * Applies `@CachePut` or `@CacheEvict` to demonstrate cache consistency mechanisms. This ensures that data updates in the primary PostgreSQL database are immediately reflected in Redis, preventing critical e-commerce errors where customers see outdated prices due to stale cache data.

---

## V. API ENDPOINT & PERFORMANCE EXPECTATIONS SUMMARY

| No. | Endpoint | Method | Applied Cache Parameter | Expected Latency (No Cache) | Expected Latency (With Cache) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `/api/products/trending` | GET | `@Cacheable(value = "trending")` | Slow ($1000\text{ms} - 3000\text{ms}$) | Extremely Fast ($<10\text{ms}$) |
| 2 | `/api/products/search` | GET | `@Cacheable(value = "search", key = "...")` | Medium to Slow (Keyword dependent) | Extremely Fast ($<5\text{ms}$) |
| 3 | `/api/products/{id}` | GET | `@Cacheable(value = "product", key = "#id")` | High risk of system crash under heavy load | Stable and highly responsive under heavy load |
| 4 | `/api/products/{id}` | PUT | `@CachePut` or `@CacheEvict` | N/A (Direct Write to DB) | Refreshes / Flushes data in Redis |