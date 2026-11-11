\# 🏨 Grand Stay – Hotel Management System



A Java-based Hotel Management System developed as an academic mini-project, featuring an interactive web interface, backend functionality, and database integration.



Grand Stay is an initial trial module developed as part of a much larger application envisioned as an ongoing college project. The goal is to explore, develop, and integrate individual modules into a larger, scalable application rather than build a standalone system.



\## 📌 Project Overview



Grand Stay aims to simplify hotel operations by providing a centralized platform to manage rooms, guest bookings, services, checkouts, and billing.



The project demonstrates the practical implementation of Java programming, web development, and database management while exploring how individual modules can eventually contribute to a larger integrated application.



\## ✨ Features



\- \*\*User Registration \& Login:\*\* Register and access the hotel management system.

\- \*\*Dashboard:\*\* View an overview of hotel operations and room statistics.

\- \*\*Room Management:\*\* Manage room details, room types, and availability.

\- \*\*Room Booking:\*\* Create and manage guest reservations.

\- \*\*Booking History:\*\* View previous and existing bookings.

\- \*\*Booking Cancellation:\*\* Cancel existing reservations.

\- \*\*Guest Checkout:\*\* Process guest checkouts.

\- \*\*Room Services:\*\* Manage additional services requested by guests.

\- \*\*Billing \& Invoices:\*\* Calculate bills and generate digital invoices.

\- \*\*Hotel Data Management:\*\* Maintain hotel-related information.



\## 🛠️ Technologies Used



| Technology | Purpose |

|---|---|

| Java | Backend development |

| HTML | Webpage structure |

| CSS | Styling and user interface |

| MySQL | Database management |

| JDBC | Java-MySQL connectivity |

| Java HttpServer | Local web server |



\## 📂 Project Structure



```text

HotelManagementSystem/

│

├── lib/

│   └── mysql-connector-j-9.4.0.jar

│

├── src/

│   └── main/

│       └── java/

│           └── com/

│               └── hotel/

│                   ├── DatabaseConnection.java

│                   ├── DatabaseTest.java

│                   └── HotelServer.java

│

├── webapp/

│   ├── css/

│   │   └── style.css

│   ├── WEB-INF/

│   ├── booking.html

│   ├── bookings.html

│   ├── dashboard.html

│   ├── index.html

│   ├── rooms.html

│   └── settings.html

│

└── README.md

```



\## ⚙️ Prerequisites



Before running the project, make sure you have:



\- Java Development Kit (JDK)

\- MySQL Server

\- MySQL Connector/J 9.4.0

\- Windows Command Prompt (CMD)



\## 🚀 How to Run the Project



\*\*Step 1: Clone the repository\*\*



```bash

git clone https://github.com/rayeah07/HotelManagementSystem.git

```



\*\*Step 2: Navigate to the project directory\*\*



```cmd

cd HotelManagementSystem

```



\*\*Step 3: Create the MySQL database\*\*



Open MySQL and execute:



```sql

CREATE DATABASE hotel\_db;

```



\*\*Step 4: Configure the database password\*\*



Set your MySQL root password as an environment variable in CMD:



```cmd

set MYSQL\_PASSWORD=your\_mysql\_password

```



Replace `your\_mysql\_password` with your actual MySQL password.



\*\*Step 5: Compile the Java files\*\*



```cmd

if not exist out mkdir out

javac -cp "lib\\mysql-connector-j-9.4.0.jar" -d out src\\main\\java\\com\\hotel\\\*.java

```



\*\*Step 6: Start the server\*\*



```cmd

java -cp "out;lib\\mysql-connector-j-9.4.0.jar" com.hotel.HotelServer

```



\*\*Step 7: Open the application\*\*



Visit:



http://localhost:8080



\## 🚧 Current Development \& Future Scope



Grand Stay is currently an evolving project, and development is still ongoing.



\*\*Currently Working On:\*\*

\- Additional functionalities and feature enhancements.

\- Improving existing modules and user experience.

\- Refining database operations and system integration.

\- Identifying opportunities for better scalability.



\*\*Long-Term Vision:\*\*



The larger goal is to develop a much bigger, integrated application throughout our college journey. Grand Stay serves as one of the initial trial modules for this broader project.



Rather than developing it as an independent, standalone application, the intention is to explore how this module can be expanded, improved, and eventually integrated into the larger system.



This project is therefore both a functional learning experience and a foundation for something bigger.



\## 🎯 Learning Outcomes



Through this project, I gained practical experience in:



\- Java backend development.

\- Connecting Java applications with MySQL using JDBC.

\- Designing web interfaces using HTML and CSS.

\- Implementing database operations and data management.

\- Understanding client-server communication.

\- Debugging and troubleshooting application errors.

\- Integrating frontend, backend, and database components.

\- Exploring modular development and application scalability.



\## 👩‍💻 Developed By



\*\*Rhea Castelino\*\*

\*\*Arlin Castelino\*\*

\*\*Reha Salvi\*\*



Academic Mini Project – Hotel Management System



\## 🔗 GitHub Repository



https://github.com/rayeah07/HotelManagementSystem



\---



\*Built with Java, HTML, CSS, and MySQL. One module today, a bigger vision for tomorrow.\*

