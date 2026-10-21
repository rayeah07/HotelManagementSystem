USE hotel_db;

CREATE TABLE rooms (
    room_number INT PRIMARY KEY,
    room_type VARCHAR(50),
    price DOUBLE,
    available BOOLEAN
);
INSERT INTO rooms VALUES
(101, 'Single', 1500, TRUE),
(102, 'Single', 1500, TRUE),
(201, 'Double', 2500, TRUE),
(301, 'Deluxe', 4000, TRUE);
SELECT * FROM rooms;
CREATE TABLE customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    phone VARCHAR(20)
);
CREATE TABLE bookings (
    booking_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    room_number INT,
    days INT,
    total DOUBLE,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (room_number) REFERENCES rooms(room_number)
);
SHOW TABLES;
SELECT * FROM rooms;
SELECT * FROM customers;
SELECT * FROM bookings;
INSERT INTO rooms VALUES
(101, 'Single', 1500, TRUE),
(102, 'Single', 1500, TRUE),
(201, 'Double', 2500, TRUE),
(301, 'Deluxe', 4000, TRUE);
SELECT * FROM rooms;
CREATE TABLE customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    phone VARCHAR(20)
);
SELECT * FROM customers;
CREATE TABLE bookings (
    booking_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    room_number INT,
    days INT,
    total DOUBLE,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (room_number) REFERENCES rooms(room_number)
);