
/*

   --------  CPCS361 Project  --------

     Section 01 | Group 06 

    | ------------------------------- |    | ------------------------------- |
    |         Group Members           |    |      Java Compiler: Javac       |
    | ------------------------------- |    | ------------------------------- |
    | Name:Shatha Alshaikh            |    |      Java Version: 1.8.0_111    |        
    | ------------------------------  |
    | ------------------------------  |    | Java Vendor: Oracle Corporation | 
    | Name: Lujain Alqarni            |    | ------------------------------  |
    | ------------------------------- |
    | Name: Shayma  Aljuaid           |     
    | ------------------------------- |

     ------------------------------------------------------------------------

    | ------------------------------- |    | ------------------------------- |  
    |     Operating System info       |    |     Hardware Configuration      |
    | ------------------------------- |    | ------------------------------- |
    |   Operating System: Windows 10  |    | Available Processors (Cores): 4 |        
    | ------------------------------  |    | ------------------------------  |
    |        OS Version: 10.0         |    |  Total Memory (JVM) (MB): 182   |
    | ------------------------------- |    | ------------------------------- |
    |     OS Architecture: amd64      |    |   Max Memory (JVM) (MB): 2677   |         
    | ------------------------------- |    | ------------------------------- |
                                           |   Free Memory (JVM) (MB): 180   |
                                           | ------------------------------- |

     ------------------------------------------------------------------------

*/
//Importing Libraries for  input/output operations,handling dates and times and managing concurrent operations with threads.
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;


// ------------------- Main class to run the restaurant simulation.------------------- 
 
public class RestaurantSimulation {

    // Define simulation time scale: 1 simulated minute = 100 ms , defines how much faster the simulation runs compared to real-time.
    static final double TIME_SCALE = 100.0;

    //-------------------Order class represents a meal order made by a customer.-------------------
     
    static class Order {
        private final Customer customer;
        private final String meal;
        private final LocalTime orderTime;
        private final int tableNumber;
        private final CompletableFuture<LocalTime> serveTimeFuture;// Future to track when the meal is served
        private final boolean isPoisonPill;
        private final StringBuilder customerLog;// Log of events for this specific order, tied to the customer
        private LocalTime prepEndTime; // To track when preparation ends

        // Constructor for regular orders
        public Order(Customer customer, String meal, LocalTime orderTime, int tableNumber) {
            this.customer = customer;
            this.meal = meal;
            this.orderTime = orderTime;
            this.tableNumber = tableNumber;
            this.serveTimeFuture = new CompletableFuture<>();
            this.isPoisonPill = false;
            this.customerLog = customer.getLog();
        }

        // Poison pill constructor (used to signal threads to stop processing)
        public Order(boolean isPoisonPill) {
            this.customer = null;
            this.meal = null;
            this.orderTime = null;
            this.tableNumber = -1;
            this.serveTimeFuture = null;
            this.isPoisonPill = isPoisonPill;
            this.customerLog = null;
        }

        public Customer getCustomer() {
            return customer;
        }

        public String getMeal() {
            return meal;
        }

        public LocalTime getOrderTime() {
            return orderTime;
        }

        public int getTableNumber() {
            return tableNumber;
        }

        public CompletableFuture<LocalTime> getServeTimeFuture() {
            return serveTimeFuture;
        }

        public boolean isPoisonPill() {
            return isPoisonPill;
        }

        public StringBuilder getCustomerLog() {
            return customerLog;
        }

        public void setPrepEndTime(LocalTime prepEndTime) {
            this.prepEndTime = prepEndTime;
        }

        public LocalTime getPrepEndTime() {
            return prepEndTime;
        }
    }//End of order class

    //------------------- Restaurant class manages tables, orders, meal preparation, and synchronization between entities.-------------------
     
    static class Restaurant {
        // A map storing the meal preparation time for each meal type
        private final Map<String, Integer> mealTimes;
        // Queue to store orders that need to be prepared
        private final BlockingQueue<Order> orderQueue;
        // Queue for ready meals waiting to be served
        private final BlockingQueue<Order> readyMealsQueue;
        private final int totalTables;
        private final LocalTime simulationStartTime;

        // BlockingQueue to manage available table numbers
        private final BlockingQueue<Integer> availableTables;

        // Map to track table availability times
        private final ConcurrentHashMap<Integer, LocalTime> tableAvailability;

        // Variables for summary
        private int totalCustomersServed = 0;
        private Duration totalWaitTime = Duration.ZERO;
        private Duration totalPreparationTime = Duration.ZERO;
        private LocalTime simulationEndTime;
        private LocalTime firstCustomerArrivalTime;

        // Lock for updating simulation time and summary
        private final ReentrantLock lock = new ReentrantLock();

        // Real start time in milliseconds to calculate current simulation time
        private final long simulationRealStartMillis;

        // BufferedWriter for logging
        private final BufferedWriter writer;

        public Restaurant(int numberOfTables, Map<String, Integer> mealTimes, LocalTime startTime,
                         long simulationRealStartMillis, BufferedWriter writer) {
            this.mealTimes = mealTimes;
            this.orderQueue = new LinkedBlockingQueue<>();
            this.readyMealsQueue = new LinkedBlockingQueue<>();
            this.totalTables = numberOfTables;
            this.simulationStartTime = startTime;
            this.simulationEndTime = startTime;

            // Initialize availableTables as a LinkedBlockingQueue with capacity equal to numberOfTables
            this.availableTables = new LinkedBlockingQueue<>(numberOfTables);
            this.tableAvailability = new ConcurrentHashMap<>();

            // Populate availableTables and tableAvailability
            for (int i = 1; i <= numberOfTables; i++) {
                this.availableTables.add(i);
                this.tableAvailability.put(i, startTime);
            }

            this.simulationRealStartMillis = simulationRealStartMillis;
            this.writer = writer;
        }

        public void setFirstCustomerArrivalTime(LocalTime FirstCustomerArrivalTime) {
            firstCustomerArrivalTime = FirstCustomerArrivalTime;
        }

        /*
          Calculates the current simulation time based on real elapsed time and time scale.
         
          @return The current simulation LocalTime.
         */
        public LocalTime getCurrentSimulationTime() {
            long elapsedRealMillis = System.currentTimeMillis() - simulationRealStartMillis;
            long elapsedSimMinutes = (long) (elapsedRealMillis / TIME_SCALE);
            return simulationStartTime.plusMinutes(elapsedSimMinutes);
        }

        /*
          -Method to take a table for a customer and log the seating event
          -return The table number assigned to the customer.
          -throws InterruptedException If the thread is interrupted.
         */
        public int takeTable(Customer customer, LocalTime arrivalTime) throws InterruptedException {
            // Acquire a table number from availableTables
            int tableNumber = availableTables.take(); // This will block if no tables are available

            // Determine seated time based on table availability and arrival time
            LocalTime tableAvailableTime = tableAvailability.getOrDefault(tableNumber, simulationStartTime);
            LocalTime seatedTime = tableAvailableTime.isAfter(arrivalTime) ? tableAvailableTime : arrivalTime;

            // Calculate wait time
            Duration waitTime = Duration.between(arrivalTime, seatedTime);
            if (waitTime.isNegative()) {
                waitTime = Duration.ZERO;
            }

            // Update summary statistics
            lock.lock();
            try {
                totalWaitTime = totalWaitTime.plus(waitTime);
                // Update simulation end time if necessary
                if (seatedTime.isAfter(simulationEndTime)) {
                    simulationEndTime = seatedTime;
                }
            } finally {
                lock.unlock();
            }

            // Assign tableNumber and seatedTime to customer
            customer.setTableNumber(tableNumber);
            customer.setSeatedTime(seatedTime);

            // Update table's next available time to when the customer will leave (seatedTime + dining duration)
            LocalTime leaveTime = seatedTime.plusMinutes(12);
            tableAvailability.put(tableNumber, leaveTime);

            return tableNumber;
        }

        //Releases a table when a customer leaves.
         
        public void leaveTable(int tableNumber, LocalTime leaveTime, int customerId) {

            // Update the table's next available time to the customer's leave time
            tableAvailability.put(tableNumber, leaveTime);
            availableTables.add(tableNumber);


            // Update simulation end time based on the last customer to leave
            lock.lock();
            try {
                if (leaveTime.isAfter(simulationEndTime)) {
                    simulationEndTime = leaveTime;
                }
            } finally {
                lock.unlock();
            }
        }

        // Place an order into the kitchen's order queue
           
        public void placeOrder(Order order) throws InterruptedException {
            orderQueue.put(order);
        }

        //Retrieves an order from the kitchen's order queue for processing by the chef
          
        public Order retrieveOrder() throws InterruptedException {
            return orderQueue.take();
        }

        //Adds a prepared meal to the ready meals queue for serving.
         
        public void addReadyMeal(Order order) throws InterruptedException {
            readyMealsQueue.put(order);
        }

        //Retrieves a prepared meal from the ready meals queue.
         
        public Order retrieveReadyMeal() throws InterruptedException {
            return readyMealsQueue.take();
        }

        /*
          Updates the simulation end time based on the given time.
          
         */
        public void updateSimulationEndTime(LocalTime eventTime) {
            lock.lock();
            try {
                if (eventTime.isAfter(simulationEndTime)) {
                    simulationEndTime = eventTime;
                }
            } finally {
                lock.unlock();
            }
        }

        //Tracks the total preparation time.
          
        public void trackPreparationTime(Duration prepTime) {
            lock.lock();
            try {
                totalPreparationTime = totalPreparationTime.plus(prepTime);
            } finally {
                lock.unlock();
            }
        }

        /*
          Increments the count of served customers.
         */
        public void incrementCustomersServed() {
            lock.lock();
            try {
                totalCustomersServed++;
            } finally {
                lock.unlock();
            }
        }

        //Retrieves the preparation time for a given meal.
     
        public int getMealTime(String meal) {
            return mealTimes.getOrDefault(meal, 10);
        }

        /*
         Prints the summary of the simulation.
         */
        public void printSummary() {
            lock.lock();
            try {
                long avgWaitTime = totalCustomersServed > 0 ? totalWaitTime.toMinutes() / totalCustomersServed : 0;
                long avgPrepTime = totalCustomersServed > 0 ? totalPreparationTime.toMinutes() / totalCustomersServed : 0;
                long totalSimTime = Duration.between(firstCustomerArrivalTime, simulationEndTime).toMinutes();

                StringBuilder summary = new StringBuilder();
                summary.append("\n[End of Simulation]\n\n");
                summary.append("Summary:\n");
                summary.append("- Total Customers Served: ").append(totalCustomersServed).append("\n");
                summary.append("- Average Wait Time for Table: ").append(avgWaitTime).append(" Minutes\n");
                summary.append("- Average Order Preparation Time: ").append(avgPrepTime).append(" Minutes\n");
                summary.append("- Total Simulation Time: ").append(totalSimTime).append(" Minutes\n");

                // Write the summary to the output file
                synchronized (writer) {
                    writer.write(summary.toString());
                    writer.flush();
                }

            } catch (IOException e) {
                System.out.println("Error writing summary to file: " + e.getMessage());
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }//End of Restaurant Class

    //------------------- Customer class simulates the customer's dining process.-------------------
     
    static class Customer implements Runnable {

        private final int id;
        private final String meal;
        private final LocalTime arrivalTime;
        private final Restaurant restaurant;
        private final StringBuilder log;

        private int tableNumber;
        private LocalTime seatedTime;

        public Customer(int id, String meal, LocalTime arrivalTime, Restaurant restaurant) {
            this.id = id;
            this.meal = meal;
            this.arrivalTime = arrivalTime;
            this.restaurant = restaurant;
            this.log = new StringBuilder();
        }

        public int getId() {
            return id;
        }

        public StringBuilder getLog() {
            return log;
        }

        public int getTableNumber() {
            return tableNumber;
        }

        public void setTableNumber(int tableNumber) {
            this.tableNumber = tableNumber;
        }

        public LocalTime getSeatedTime() {
            return seatedTime;
        }

        public void setSeatedTime(LocalTime seatedTime) {
            this.seatedTime = seatedTime;
        }

        //Appends a log entry with timestamp.
         
        private void logEvent(LocalTime time, String message) {
            log.append("[").append(time.format(DateTimeFormatter.ofPattern("HH:mm"))).append("] ")
               .append(message).append("\n");
        }

        @Override
        public void run() {
            try {
                // Calculate simulated arrival delay in minutes
                long arrivalDelayMinutes = Duration.between(restaurant.simulationStartTime, arrivalTime).toMinutes();
                if (arrivalDelayMinutes > 0) {
                    Thread.sleep((long) (arrivalDelayMinutes * RestaurantSimulation.TIME_SCALE)); // 1 simulated minute = 100ms
                }

                // Log arrival
                logEvent(arrivalTime, "Customer " + id + " arrives.");

                // Try to seat at a table
                int tableNumber = restaurant.takeTable(this, arrivalTime);
                LocalTime seatedTime = this.getSeatedTime();

                // Log seating
                logEvent(seatedTime, "Customer " + id + " is seated at Table " + tableNumber + ".");

                // Simulate time before placing order (1 minute)
                LocalTime orderTime = seatedTime.plusMinutes(1);
                Thread.sleep((long) (1 * RestaurantSimulation.TIME_SCALE)); // 1 simulated minute = 100ms

                // Log order placement
                logEvent(orderTime, "Customer " + id + " places an order: " + meal + ".");

                // Create an order and place it in the order queue
                Order order = new Order(this, meal, orderTime, tableNumber);
                restaurant.placeOrder(order);

                // Wait for the meal to be served
                LocalTime serveTime = order.getServeTimeFuture().get(); // Blocks until served

                // Simulate eating time (fixed 12 minutes)
                int eatingTime = 12;
                Thread.sleep((long) (eatingTime * RestaurantSimulation.TIME_SCALE)); // 12 simulated minutes = 1200ms

                // Calculate departure time
                LocalTime departureTime = serveTime.plusMinutes(eatingTime);

                // Log departure
                logEvent(departureTime, "Customer " + id + " finishes eating and leaves the restaurant.");
                logEvent(departureTime, "Table " + tableNumber + " is now available.\n");

                // Release the table
                restaurant.leaveTable(tableNumber, departureTime, id);

                // Update summary
                restaurant.incrementCustomersServed();
                restaurant.updateSimulationEndTime(departureTime);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Optionally log the interruption
                logEvent(LocalTime.now(), "Customer " + id + " was interrupted.");
            } catch (ExecutionException e) {
                // Optionally log the exception
                logEvent(LocalTime.now(), "Error serving Customer " + id + ": " + e.getMessage());
            } finally {
                // Write the full log for this customer to the output file
                try {
                    synchronized (restaurant.writer) {
                        restaurant.writer.write(log.toString());
                        restaurant.writer.flush();
                    }
                } catch (IOException e) {
                    System.out.println("Error writing customer log to file: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }//End of Customer Class 

    //-------------------Chef class to prepare orders sequentially.-------------------
     
    static class Chef implements Runnable {

        private final int id;
        private final Restaurant restaurant;

        public Chef(int id, Restaurant restaurant) {
            this.id = id;
            this.restaurant = restaurant;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Retrieve an order from the ordersQueue (a shared queue that holds customer orders waiting to be prepared)
                    Order order = restaurant.retrieveOrder();
                    // A poison pill is a special order marked as true that signals the chef to stop working.
                    if (order.isPoisonPill()) {
                        // Exit loop and the thread will ends 
                        break;
                    }

                    String meal = order.getMeal();
                    int prepTime = restaurant.getMealTime(meal);

                    // Get current simulation time
                    LocalTime currentSimTime = restaurant.getCurrentSimulationTime();

                    // Calculate prepEndTime based on current simulation time
                    LocalTime prepEndTime = currentSimTime.plusMinutes(prepTime);
                    order.setPrepEndTime(prepEndTime);

                    // Log preparation start in customer's log
                    synchronized (order.getCustomerLog()) {
                        order.getCustomerLog().append("[").append(currentSimTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                            .append("] Chef ").append(id).append(" starts preparing ").append(meal)
                            .append(" for Customer ").append(order.getCustomer().getId()).append(".\n");
                    }

                    // Simulate meal preparation time
                    Thread.sleep((long) (prepTime * TIME_SCALE)); // 1 simulated minute = 100ms

                    // Log preparation completion in customer's log
                    synchronized (order.getCustomerLog()) {
                        order.getCustomerLog().append("[").append(prepEndTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                            .append("] Chef ").append(id).append(" finishes preparing ").append(meal)
                            .append(" for Customer ").append(order.getCustomer().getId()).append(".\n");
                    }

                    // Add to ready meals queue
                    restaurant.addReadyMeal(order);

                    // Update summary
                    restaurant.trackPreparationTime(Duration.ofMinutes(prepTime));
                    restaurant.updateSimulationEndTime(prepEndTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Optionally log the interruption
                try {
                    synchronized (restaurant.writer) {
                        restaurant.writer.write("Chef " + id + " was interrupted.\n");
                        restaurant.writer.flush();
                    }
                } catch (IOException ioException) {
                    System.out.println("Error writing chef interruption to file: " + ioException.getMessage());
                    ioException.printStackTrace();
                }
            }
        }
    }//End of Chef class

    //-------------------Waiter class to serve meals sequentially with a 1-minute wait between each meal.-------------------
     
    
    static class Waiter implements Runnable {

        private final int id;
        private final Restaurant restaurant;

        public Waiter(int id, Restaurant restaurant) {
            this.id = id;
            this.restaurant = restaurant;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Retrieve a ready meal from the restaurant
                    Order order = restaurant.retrieveReadyMeal();

                    // A poison pill is a special order marked as true that signals the waiter to stop working.
                    if (order.isPoisonPill()) {
                        // Exit loop and the thread will ends
                        break;
                    }

                    // Get the meal details
                    String meal = order.getMeal();
                    LocalTime prepEndTime = order.getPrepEndTime(); // Time when the chef finished preparing it

                    // Get the current simulation time (when the waiter starts serving)
                    LocalTime currentSimTime = restaurant.getCurrentSimulationTime();

                    // Log the serving action in the customer's log
                    synchronized (order.getCustomerLog()) {
                        order.getCustomerLog().append("[").append(currentSimTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                            .append("] Waiter ").append(id).append(" serves ").append(meal)
                            .append(" to Customer ").append(order.getCustomer().getId())
                            .append(" at Table ").append(order.getTableNumber()).append(".\n");
                    }

                    // Notify the customer that the meal has been served
                    order.getServeTimeFuture().complete(currentSimTime);

                    // Update the simulation end time (this would be the time when the meal is served)
                    restaurant.updateSimulationEndTime(currentSimTime);

                    // Wait for 1 minute before serving the next meal
                    Thread.sleep((long) (1 * TIME_SCALE)); // Simulate the 1-minute wait (scaled by TIME_SCALE)
                }
            } catch (InterruptedException e) {
                // Handle interruptions properly by restoring the interrupt status
                Thread.currentThread().interrupt();
                // Optionally log the interruption
                try {
                    synchronized (restaurant.writer) {
                        restaurant.writer.write("Waiter " + id + " was interrupted.\n");
                        restaurant.writer.flush();
                    }
                } catch (IOException ioException) {
                    System.out.println("Error writing waiter interruption to file: " + ioException.getMessage());
                    ioException.printStackTrace();
                }
            }
        }
    }//End of Waiter class

    
    //-------------------Main method to run the simulation.-------------------
     
public static void main(String[] args) {
    // Array of String for both inputFiles and outputFiles to read then write 
    String[] inputFiles = {"restaurant_simulation_input1.txt", "restaurant_simulation_input2.txt", "restaurant_simulation_input3.txt"};
    String[] outputFiles = {"restaurant_simulation_output1.txt", "restaurant_simulation_output2.txt", "restaurant_simulation_output3.txt"};
    
    //Loop to read and write in the file orderly
    for (int fileIndex = 0; fileIndex < inputFiles.length; fileIndex++) {
        String inputFile = inputFiles[fileIndex];
        String outputFile = outputFiles[fileIndex];

        //The number of chefs, waiters, and tables in the restaurant.
        int numChefs = 0, numWaiters = 0, numTables = 0;
        //A map of meal names to their corresponding preparation times (in minutes).
        Map<String, Integer> mealTimes = new HashMap<>();
        //A list to hold customer order details, such as their ID, arrival time, and meal choice.
        List<String[]> customerData = new ArrayList<>();

        // Read the input file and split it into different sections
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;

            // Read configuration line
            if ((line = br.readLine()) != null) {
                String[] config = line.trim().split(" ");
                for (String conf : config) {
                    String[] parts = conf.split("=");
                    if (parts.length != 2) continue;
                    //The first line of the input defines the number of chefs, waiters, and tables .
                    switch (parts[0]) {
                        case "NC":
                            numChefs = Integer.parseInt(parts[1]);
                            break;
                        case "NW":
                            numWaiters = Integer.parseInt(parts[1]);
                            break;
                        case "NT":
                            numTables = Integer.parseInt(parts[1]);
                            break;
                        default:
                            System.out.println("Unknown configuration: " + parts[0]);
                    }
                }
            }

            // Read meal preparation times
            if ((line = br.readLine()) != null) {
                String[] meals = line.trim().split(" ");
                for (String meal : meals) {
                    String[] parts = meal.split("=");
                    if (parts.length != 2) continue;
                    String mealName = parts[0];
                    String prepTimeStr = parts[1];
                    String[] timeParts = prepTimeStr.split(":");
                    if (timeParts.length != 2) continue;
                    int prepTime = Integer.parseInt(timeParts[1]);
                    mealTimes.put(mealName, prepTime);
                }
            }

            // Read customer data
            while ((line = br.readLine()) != null) {
                String[] customerInfo = line.trim().split(" ");
                if (customerInfo.length != 3) continue;
                customerData.add(customerInfo);
            }

        } catch (IOException e) {
            System.out.println("Error reading input file: " + inputFile + " - " + e.getMessage());
            e.printStackTrace();
            continue;
        }

        // Record the real start time
        long simulationRealStartMillis = System.currentTimeMillis();
        // Define simulation start time 
        LocalTime SIMULATION_START_TIME = LocalTime.of(8, 0);
        
        // Initialize restaurant with the BufferedWriter
        // Note: The BufferedWriter 'writer' is already initialized in the try-with-resources block
        // So, the Restaurant initialization should be inside the try block
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            Restaurant restaurant = new Restaurant(numTables, mealTimes, SIMULATION_START_TIME,
                    simulationRealStartMillis, writer);

            // Log simulation start
            writer.write("Simulation Started with " + numChefs + " Chefs, " + numWaiters + " Waiters, and " + numTables + " Tables.\n\n");

            // Start chef threads
            ExecutorService chefService = Executors.newFixedThreadPool(numChefs);
            for (int i = 1; i <= numChefs; i++) {
                chefService.submit(new Chef(i, restaurant));
            }

            // Start waiter threads
            ExecutorService waiterService = Executors.newFixedThreadPool(numWaiters);
            for (int i = 1; i <= numWaiters; i++) {
                waiterService.submit(new Waiter(i, restaurant));
            }

            LocalTime firstCustomerArrivalTime = null;
            // Start customer threads
            List<Thread> customerThreads = new ArrayList<>();

            /*For each customer, the details are parsed and a new Customer object is created. 
                Each customer runs on a separate thread to simulate the process 
                of arriving,ordering, being served and eating.*/
            for (String[] customerInfo : customerData) {
                int customerId = Integer.parseInt(customerInfo[0].split("=")[1]);
                String arrivalTimeStr = customerInfo[1].split("=")[1];
                String orderMeal = customerInfo[2].split("=")[1];
                LocalTime arrivalTime = LocalTime.parse(arrivalTimeStr, DateTimeFormatter.ofPattern("HH:mm"));

                Customer customer = new Customer(customerId, orderMeal, arrivalTime, restaurant);
                Thread customerThread = new Thread(customer);
                customerThread.start();
                customerThreads.add(customerThread);

                if (customerId == 1) {
                    firstCustomerArrivalTime = arrivalTime;
                }
            }

            // Wait for all customer threads to finish
            for (Thread t : customerThreads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }

            // After all customers have been processed, send poison pills to chefs and waiters
            try {
                for (int i = 0; i < numChefs; i++) {
                    restaurant.placeOrder(new Order(true));
                }
                for (int i = 0; i < numWaiters; i++) {
                    restaurant.readyMealsQueue.put(new Order(true));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }

            // Shutdown chef and waiter services
            chefService.shutdown();
            waiterService.shutdown();
            try {
                if (!chefService.awaitTermination(60, TimeUnit.SECONDS)) {
                    chefService.shutdownNow();
                }
                if (!waiterService.awaitTermination(60, TimeUnit.SECONDS)) {
                    waiterService.shutdownNow();
                }
            } catch (InterruptedException e) {
                chefService.shutdownNow();
                waiterService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Print simulation summary
            restaurant.setFirstCustomerArrivalTime(firstCustomerArrivalTime);
            restaurant.printSummary();

        } catch (IOException e) {
            System.out.println("Error writing to output file: " + outputFile + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
}//End of the main


    
}//THE END
