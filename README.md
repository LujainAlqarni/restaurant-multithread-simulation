🍽️ Multi-threaded Restaurant Simulation with Synchronization
A Java-based multi-threaded restaurant simulation developed for Operating System Course.

The project simulates the interaction between customers, chefs, waiters, and restaurant tables using Java threads, synchronization, blocking queues, locks, and concurrent data structures.


📖 Introduction
This project focuses on simulating the dynamic environment of a restaurant where multiple entities operate concurrently.

The simulation models three main types of workers and participants:

Customers who arrive at different times, wait for available tables, place orders, eat, and leave.
Chefs who retrieve customer orders and prepare meals.
Waiters who retrieve prepared meals and serve them to customers.
Since these activities occur concurrently, the project uses Java's multithreading and concurrency mechanisms to coordinate access to shared resources and ensure that the restaurant operates correctly.

The simulation also handles situations such as customers waiting for available tables and meals waiting to be prepared or served.

🎯 Purpose of the Project
The main purpose of this project is to demonstrate how operating-system concepts can be applied to a real-world scenario involving multiple concurrent processes/threads.

The simulation specifically demonstrates:

Multithreading
Thread synchronization
Mutual exclusion
Inter-thread communication
Producer-consumer behavior
Shared resource management
Blocking queues
Locks
Thread pools
Concurrent data structures
Thread interruption and termination
The restaurant scenario provides a practical way to understand how multiple threads interact while accessing shared resources.

🏗️ System Overview
The restaurant simulation consists of four main components:

                    ┌────────────────────┐
                    │     Customers      │
                    │   (Threads)        │
                    └─────────┬──────────┘
                              │
                              │ Place Orders
                              ▼
                    ┌────────────────────┐
                    │    Order Queue     │
                    │ BlockingQueue      │
                    └─────────┬──────────┘
                              │
                              │ Retrieve Orders
                              ▼
                    ┌────────────────────┐
                    │       Chefs        │
                    │   (Worker Threads) │
                    └─────────┬──────────┘
                              │
                              │ Prepared Meals
                              ▼
                    ┌────────────────────┐
                    │ Ready Meals Queue  │
                    │ BlockingQueue      │
                    └─────────┬──────────┘
                              │
                              │ Retrieve Meals
                              ▼
                    ┌────────────────────┐
                    │      Waiters       │
                    │   (Worker Threads) │
                    └─────────┬──────────┘
                              │
                              │ Serve Meal
                              ▼
                    ┌────────────────────┐
                    │     Customer       │
                    │   Eats & Leaves    │
                    └────────────────────┘

🧩 Main Components
1. Order
The Order class represents a customer's meal order.

Each order contains information such as:

Customer
Meal name
Order time
Table number
Meal preparation completion time
Serve-time future
Customer-specific log
The class also supports a poison pill mechanism used to signal worker threads that they should stop processing.

2. Restaurant
The Restaurant class manages the shared resources and overall state of the simulation.

It is responsible for:

Managing restaurant tables
Managing customer orders
Managing prepared meals
Tracking table availability
Tracking waiting time
Tracking meal preparation time
Tracking the number of served customers
Updating the simulation end time
Generating the final simulation summary
The class uses synchronization mechanisms to safely update shared statistics.

3. Customer
Each customer runs as an independent Java thread.

The customer's lifecycle is:

Arrival
   ↓
Wait for Available Table
   ↓
Get Seated
   ↓
Place Order
   ↓
Wait for Meal
   ↓
Eat for 12 Minutes
   ↓
Leave Restaurant

Customers may have to wait when all restaurant tables are occupied.

Each customer also maintains a log containing the events that occur during their visit.

4. Chef
Chefs are implemented as worker threads using an ExecutorService.

Each chef repeatedly:

Retrieves an order from the order queue.
Determines the meal preparation time.
Starts preparing the meal.
Sleeps for the simulated preparation duration.
Marks the meal as prepared.
Places the prepared order into the ready-meals queue.
Multiple chefs can work concurrently, allowing multiple meals to be prepared at the same time.

5. Waiter
Waiters are also implemented as worker threads.

Each waiter:

Retrieves a prepared meal.
Logs the serving event.
Notifies the corresponding customer that the meal is ready.
Updates the simulation state.
Waits for one simulated minute before serving the next meal.
The waiter uses CompletableFuture to notify the customer thread that its order has been served.

🔄 Concurrency Model
The project uses multiple types of threads.

Customer Threads
A separate thread is created for every customer.

Thread customerThread = new Thread(customer);
customerThread.start();

This allows customers to arrive and interact with the restaurant concurrently.

Chef Threads
Chef threads are managed through a fixed thread pool:

ExecutorService chefService =
        Executors.newFixedThreadPool(numChefs);

Waiter Threads
Waiters are also managed through a fixed thread pool:

ExecutorService waiterService =
        Executors.newFixedThreadPool(numWaiters);

This design allows the number of chefs and waiters to be configured through the input file.

🔐 Synchronization and Concurrency Mechanisms
One of the main goals of this project is to demonstrate synchronization in a multi-threaded environment.

BlockingQueue
Two BlockingQueue objects are used:

private final BlockingQueue<Order> orderQueue;
private final BlockingQueue<Order> readyMealsQueue;

They are used to coordinate communication between:

Customers → Chefs
Chefs → Waiters

BlockingQueue automatically handles situations where a thread needs to wait for an item to become available.

ReentrantLock
A ReentrantLock is used to protect shared simulation statistics:

private final ReentrantLock lock = new ReentrantLock();

It protects variables such as:

Total customers served
Total waiting time
Total preparation time
Simulation end time
For example:

lock.lock();

try {
    totalCustomersServed++;
} finally {
    lock.unlock();
}

This prevents multiple threads from modifying shared statistics simultaneously.

ConcurrentHashMap
The restaurant uses a ConcurrentHashMap to maintain table availability:

private final ConcurrentHashMap<Integer, LocalTime> tableAvailability;

This allows multiple threads to access table availability safely.

CompletableFuture
Each order contains a CompletableFuture:

private final CompletableFuture<LocalTime> serveTimeFuture;

The customer waits for the meal:

LocalTime serveTime =
        order.getServeTimeFuture().get();

When the waiter serves the meal, the waiter completes the future:

order.getServeTimeFuture().complete(currentSimTime);

This provides communication between the waiter thread and the corresponding customer thread.

synchronized
Customer logs and file output are protected using synchronization.

For example:

synchronized (order.getCustomerLog()) {
    // Update customer log
}

The output writer is also synchronized to prevent multiple threads from writing to the file simultaneously.

🍽️ Table Management
Restaurant tables are represented using a BlockingQueue<Integer>:

private final BlockingQueue<Integer> availableTables;

When a customer needs a table:

int tableNumber = availableTables.take();

If no table is available, the customer thread blocks until another table becomes available.

When the customer leaves:

availableTables.add(tableNumber);

The table becomes available for another customer.

This demonstrates the management of a shared resource using concurrency mechanisms.

⏱️ Simulation Time
The project uses a simulated time scale to make the restaurant simulation run faster than real time.

static final double TIME_SCALE = 100.0;

Therefore:

1 simulated minute = 100 milliseconds

For example, a 12-minute eating period is simulated using:

Thread.sleep((long) (12 * TIME_SCALE));

This allows the complete restaurant simulation to run within a short amount of real time.

📝 Input Files
The program processes three input files:

restaurant_simulation_input1.txt
restaurant_simulation_input2.txt
restaurant_simulation_input3.txt

Each input file contains:

Restaurant configuration
Meal preparation times
Customer information
Restaurant Configuration
The first line defines:

NC = Number of Chefs
NW = Number of Waiters
NT = Number of Tables

Example:

NC=2 NW=2 NT=3

Meal Preparation Times
The second line contains meal names and their preparation times.

The program parses the preparation time from the input and stores it in a HashMap.

Conceptually:

Meal = Preparation Time

Customer Information
Each customer record contains:

Customer ID
Arrival Time
Meal

For example:

C=1 T=08:05 M=Pizza

The program parses the customer ID, arrival time, and requested meal before creating a customer thread.

📤 Output Files
The simulation generates three output files:

restaurant_simulation_output1.txt
restaurant_simulation_output2.txt
restaurant_simulation_output3.txt

Each output file contains the events generated during the simulation.

The logs may include:

Customer arrival
Customer seating
Order placement
Chef starting preparation
Chef finishing preparation
Waiter serving the meal
Customer finishing their meal
Table becoming available
Simulation summary
📊 Simulation Summary
At the end of each simulation, the program generates a summary containing:

Total Customers Served
Average Wait Time for Table
Average Order Preparation Time
Total Simulation Time

Example structure:

[End of Simulation]

Summary:
- Total Customers Served: ...
- Average Wait Time for Table: ... Minutes
- Average Order Preparation Time: ... Minutes
- Total Simulation Time: ... Minutes

🛑 Thread Termination
After all customers finish, the program uses poison pills to tell chef and waiter threads to stop.

A poison pill is a special Order object:

new Order(true)

The worker thread checks:

if (order.isPoisonPill()) {
    break;
}

This provides a controlled way to terminate the worker threads after all orders have been processed.

The executor services are then shut down:

chefService.shutdown();
waiterService.shutdown();



💻 Requirements
To compile and run the project, you need:

Java Development Kit (JDK)
Java compiler (javac)
Java Runtime Environment
A text editor or Java IDE
The project was originally developed and tested using:

Environment	Version
Java	1.8.0_111
Java Vendor	Oracle Corporation
Operating System	Windows 10
OS Version	10.0
Architecture	amd64
Available Processors	4 cores


🔧 Technologies Used
Java
Java Threads
ExecutorService
BlockingQueue
CompletableFuture
ReentrantLock
ConcurrentHashMap
Java I/O
Java Time API
Synchronization




📌 Important Design Decisions
Shared Queues
The two queues separate the stages of the restaurant workflow:

Order Queue
Customers → Chefs

Ready Meals Queue
Chefs → Waiters

This prevents customers, chefs, and waiters from needing to directly control each other's execution.

Independent Customer Threads
Each customer is represented by a separate thread. This makes it possible for customers with different arrival times to interact with the restaurant concurrently.

Worker Thread Pools
Chefs and waiters are managed using fixed-size thread pools. The number of workers is determined by the input configuration.

Protected Shared Statistics
Simulation statistics are shared between multiple threads, so a ReentrantLock is used to prevent race conditions while updating them.

📈 Expected Behavior
Depending on the number of tables, chefs, waiters, and customers specified in each input file:

Customers may be seated immediately or wait for a table.
Multiple chefs may prepare different meals simultaneously.
Prepared meals wait in the ready-meals queue until a waiter serves them.
Customers remain blocked until their meals are served.
Tables become available after customers finish eating.
The simulation ends after all customers have completed their visits.
A summary is generated for each input scenario.



📚 Course Information
Course	 Operating System
Semester	Fall 2024
Submission Date	28 November 2024

👥 Group Members
Lujain Alqarni
Shayma  Aljuaid	
Shatha  Alshaikh	


📜 Academic Project
This repository contains an academic project developed as part of the Operating System course .

The project was developed for educational purposes to demonstrate concepts related to operating systems, concurrency, synchronization, and multi-threaded programming.
