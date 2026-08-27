 
# 🍽️ Multi-threaded Restaurant Simulation with Synchronization

A **Java-based multi-threaded restaurant simulation** developed for the Operating Systems course.

The project simulates real-time interactions between **Customers**, **Chefs**, **Waiters**, and **Restaurant Tables** using Java threads, synchronization primitives, blocking queues, explicit locks, and concurrent data structures.

---

## 📖 Introduction

This project focuses on simulating the dynamic environment of a restaurant where multiple entities operate concurrently.

The simulation models three primary types of active participants:

* 👤 **Customers:** Arrive at scheduled times, wait for available tables, place orders, eat, and depart.
* 👨‍🍳 **Chefs:** Retrieve pending customer orders and prepare meals.
* 🤵 **Waiters:** Retrieve prepared meals and serve them to customers.

Because these activities happen concurrently, the project leverages **Java's multithreading and concurrency mechanisms** to coordinate access to shared resources, handle waiting queues, and guarantee system stability without deadlocks or race conditions.

---

## 🎯 Purpose of the Project

The core purpose is to demonstrate how essential **Operating System (OS)** concepts apply to a real-world concurrent environment.

### Key Concepts Applied

* Multithreading & Thread Pools
* Thread Synchronization & Mutual Exclusion
* Inter-thread Communication & CompletableFuture
* Producer-Consumer Pattern
* Shared Resource Management
* Blocking Queues & Concurrent Data Structures
* Thread Interruption & Controlled Termination (Poison Pill Pattern)

---

## 🏗️ System Overview

The system architecture relies on an asynchronous pipeline pattern:

```text
 ┌────────────────────┐
 │     Customers      │ (Threads)
 └─────────┬──────────┘
           │ Place Orders
           ▼
 ┌────────────────────┐
 │    Order Queue     │ (BlockingQueue)
 └─────────┬──────────┘
           │ Retrieve Orders
           ▼
 ┌────────────────────┐
 │       Chefs        │ (Worker Threads)
 └─────────┬──────────┘
           │ Prepared Meals
           ▼
 ┌────────────────────┐
 │ Ready Meals Queue  │ (BlockingQueue)
 └─────────┬──────────┘
           │ Retrieve Meals
           ▼
 ┌────────────────────┐
 │      Waiters       │ (Worker Threads)
 └─────────┬──────────┘
           │ Serve Meal
           ▼
 ┌────────────────────┐
 │      Customer      │ Eats & Leaves
 └────────────────────┘

```

---

## 🧩 Main Components

### 1. `Order`

Represents a customer's order inside the restaurant.

* **Attributes:** Customer reference, meal name, order time, table number, preparation completion time, serve-time future (`CompletableFuture`), and customer-specific log.
* **Poison Pill Support:** Includes a flag to signal worker threads to shut down safely.

### 2. `Restaurant`

Manages global shared resources and tracks metrics.

* **Responsibilities:** Tables, order/meal queues, waiting time metrics, preparation time statistics, total served customers, and simulation end-time updates.
* Uses synchronization to safely aggregate execution metrics.

### 3. `Customer`

Executes as an independent Java thread.

**Lifecycle:**

1. Arrival
2. Wait for Available Table
3. Get Seated
4. Place Order
5. Wait for Meal
6. Eat for 12 Minutes (Simulated)
7. Leave Restaurant

### 4. `Chef`

Worker thread executing inside an `ExecutorService`.

* Fetches an order from `orderQueue`.
* Computes preparation duration and sleeps to simulate cooking.
* Places the finished meal into `readyMealsQueue`.

### 5. `Waiter`

Worker thread serving completed orders.

* Fetches a prepared meal from `readyMealsQueue`.
* Triggers `CompletableFuture` to notify the waiting customer thread.
* Waits 1 simulated minute before processing the next serving task.

---

## 🔐 Synchronization & Concurrency Mechanisms

| Mechanism | Component / Usage | Purpose |
| --- | --- | --- |
| **`BlockingQueue`** | `orderQueue`, `readyMealsQueue` | Coordinates Producer-Consumer pattern between entities. |
| **`ReentrantLock`** | Shared metrics (`lock.lock()`) | Protects counters like `totalCustomersServed` from race conditions. |
| **`ConcurrentHashMap`** | `tableAvailability` | Provides thread-safe lookup/updates for table statuses. |
| **`CompletableFuture`** | `serveTimeFuture` | Synchronizes completion signal between Waiter and Customer threads. |
| **`synchronized`** | Loggers & File Writers | Ensures sequential, un-corrupted file and log writing. |

---

## 🍽️ Resource & Time Management

### Table Management

Tables are managed using a bounded `BlockingQueue<Integer>`:

```java
// Seating a customer
int tableNumber = availableTables.take(); // Blocks if no table is available

// Releasing table after eating
availableTables.add(tableNumber);

```

### ⏱️ Simulated Time Scale

To avoid long real-time delays, the simulation scales down time using a constant:


$$\text{1 Simulated Minute} = 100 \text{ Milliseconds}$$

```java
static final double TIME_SCALE = 100.0;
// Example: 12-minute eating period
Thread.sleep((long) (12 * TIME_SCALE));

```

---

## 📝 Inputs & Outputs

### Input Files

The input files contain three main types of information:

- Restaurant configuration (chefs, waiters, and tables)
- Meal names and preparation times
- Customer ID, arrival time, and ordered meal

Example:

```text
NC=3 NW=4 NT=5
Burger=00:8 Pizza=00:10 Pasta=00:10
CustomerID=1 ArrivalTime=12:00 Order=Burger
```

### Output Files
The output files contain the simulation events in chronological order, including:

Customer arrival and seating
Order placement
Meal preparation
Meal serving
Customer departure
Final simulation summary

Example:
```text
[08:00] Customer 1 arrives.
[08:01] Customer 1 places an order: Burger.
[08:09] Chef 2 finishes preparing Burger.
[08:09] Waiter 1 serves Burger.
[08:21] Customer 1 leaves.
```
Summary:
- Total Customers Served: 5
- Average Wait Time: 2 Minutes
- Average Preparation Time: 8 Minutes



---

## 🛑 Controlled Thread Termination

The system relies on the **Poison Pill Pattern** to stop consumer threads cleanly once all input orders are processed:

```java
// Poison Pill insertion
orderQueue.put(new Order(true));

// Worker Check Inside Run Loop
if (order.isPoisonPill()) {
    break; // Graceful exit
}

// Thread Pool Shutdown
chefService.shutdown();
waiterService.shutdown();

```

---

## 💻 Environment & Requirements

### System Requirements

* **Java Development Kit (JDK):** Version 8 or higher
* **Tools:** `javac`, Java Runtime (`java`), IDE or CLI

### Target Environment Specifications

| Parameter | Value |
| --- | --- |
| **Java Version** | `1.8.0_111` (Oracle Corporation) |
| **Operating System** | Windows 10 (amd64) |
| **Available Processors** | 4 Cores |

---

## 👥 Group Members & Course Info

* **Course:** Operating Systems
* **Semester:** Fall 2024
* **Submission Date:** 28 November 2024


**Team Members:**

* Lujain Alqarni
* Shayma Aljuaid
* Shatha Alshaikh

---

## 📜 Academic Project
This repository contains an academic project developed as part of the Operating System Course.

The project was developed for educational purposes to demonstrate concepts related to operating systems, concurrency, synchronization, and multi-threaded programming.
