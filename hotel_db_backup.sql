-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: hotel_db
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `bookings`
--

DROP TABLE IF EXISTS `bookings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bookings` (
  `booking_id` int NOT NULL AUTO_INCREMENT,
  `customer_id` int NOT NULL,
  `room_number` int NOT NULL,
  `days` int NOT NULL,
  `total` double NOT NULL,
  `booking_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `checkout_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`booking_id`),
  KEY `customer_id` (`customer_id`),
  KEY `room_number` (`room_number`),
  CONSTRAINT `bookings_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`customer_id`),
  CONSTRAINT `bookings_ibfk_2` FOREIGN KEY (`room_number`) REFERENCES `rooms` (`room_number`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bookings`
--

LOCK TABLES `bookings` WRITE;
/*!40000 ALTER TABLE `bookings` DISABLE KEYS */;
INSERT INTO `bookings` VALUES (1,1,402,1,1500,'2026-09-30 12:03:36','CHECKED_OUT','2026-09-30 12:06:06'),(2,2,201,6,9000,'2026-09-30 12:38:40','CHECKED_OUT','2026-09-30 12:41:47'),(3,3,803,4,16000,'2026-09-30 12:54:50','CHECKED_OUT','2026-09-30 12:55:31'),(4,4,702,1,1500,'2026-09-30 12:56:24','CANCELLED',NULL),(5,5,102,1,1500,'2026-09-30 13:05:35','CHECKED_OUT','2026-09-30 13:09:34'),(6,6,601,5,7500,'2026-09-30 13:10:33','CHECKED_OUT','2026-10-08 09:44:06'),(7,7,303,4,16000,'2026-09-30 13:11:08','CHECKED_OUT','2026-10-08 09:43:55'),(8,8,803,1,4000,'2026-10-08 10:31:05','CHECKED_OUT','2026-10-08 10:32:02'),(9,9,503,19,76000,'2026-10-08 10:41:56','CHECKED_OUT','2026-10-08 10:45:03'),(10,10,701,27,40500,'2026-10-08 11:00:13','CHECKED_OUT','2026-10-08 11:00:50'),(11,11,601,2,3000,'2026-10-08 11:03:16','CHECKED_OUT','2026-10-21 12:29:52'),(13,13,503,8,32000,'2026-10-21 13:06:45','CHECKED_OUT','2026-10-21 13:07:03'),(14,14,803,4,16000,'2026-10-21 13:08:53','CHECKED_OUT','2026-10-22 09:45:58'),(15,15,703,2,8000,'2026-10-21 13:09:45','CHECKED_OUT','2026-10-22 09:46:02'),(16,16,603,8,32000,'2026-10-22 09:43:14','CHECKED_OUT','2026-10-22 09:46:06'),(17,17,103,10,40000,'2026-10-22 09:44:57','CHECKED_OUT','2026-10-22 09:47:53'),(18,18,403,5,20000,'2026-10-25 06:19:26','ACTIVE',NULL),(19,19,502,6,15000,'2026-10-25 06:19:40','ACTIVE',NULL),(20,20,303,3,12000,'2026-10-25 06:20:27','CHECKED_OUT','2026-11-07 11:28:36'),(21,21,501,4,4000,'2026-11-07 11:28:30','ACTIVE',NULL);
/*!40000 ALTER TABLE `bookings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `customer_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `phone` varchar(20) NOT NULL,
  PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customers`
--

LOCK TABLES `customers` WRITE;
/*!40000 ALTER TABLE `customers` DISABLE KEYS */;
INSERT INTO `customers` VALUES (1,'Rhea','1234567891'),(2,'Arlin','9876543211'),(3,'Reha','6767676767'),(4,'Kimberly','4545454545'),(5,'Joel','1231231231'),(6,'Lyka','3451236783'),(7,'Ruth','7893241597'),(8,'Arlin','8787676723'),(9,'Reha','1234567891'),(10,'rhea','1234457891'),(11,'arlin','2537264718'),(12,'reha','32454556657'),(13,'kimberly','9969978281'),(14,'ruth','4561239871'),(15,'rhea','8191612180'),(16,'arlin','1234567891'),(17,'Reha Salvi','6767676767'),(18,'reha','6348469493'),(19,'rhea','38429448234'),(20,'arlin','4636534543'),(21,'joel','8902356724');
/*!40000 ALTER TABLE `customers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `room_services`
--

DROP TABLE IF EXISTS `room_services`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_services` (
  `service_id` int NOT NULL AUTO_INCREMENT,
  `booking_id` int NOT NULL,
  `service_name` varchar(100) NOT NULL,
  `quantity` int NOT NULL,
  `price` double NOT NULL,
  `total` double NOT NULL,
  `service_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`service_id`),
  KEY `booking_id` (`booking_id`),
  CONSTRAINT `room_services_ibfk_1` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`booking_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `room_services`
--

LOCK TABLES `room_services` WRITE;
/*!40000 ALTER TABLE `room_services` DISABLE KEYS */;
INSERT INTO `room_services` VALUES (1,1,'Breakfast',2,300,600,'2026-09-30 12:05:38'),(2,2,'Breakfast',1,300,300,'2026-09-30 12:41:29'),(3,2,'Laundry',1,250,250,'2026-09-30 12:41:41'),(4,3,'Breakfast',1,300,300,'2026-09-30 12:55:02'),(5,3,'Tea / Coffee',9,150,1350,'2026-09-30 12:55:12'),(6,3,'Breakfast',1,300,300,'2026-09-30 12:55:20'),(7,5,'Breakfast',2,300,600,'2026-09-30 13:08:19'),(8,5,'Lunch',2,500,1000,'2026-09-30 13:08:30'),(9,5,'Dinner',2,600,1200,'2026-09-30 13:09:06'),(10,5,'Tea / Coffee',2,150,300,'2026-09-30 13:09:28'),(11,8,'Tea / Coffee',2,150,300,'2026-10-08 10:31:33'),(12,8,'Laundry',67,250,16750,'2026-10-08 10:31:54'),(13,9,'Breakfast',1,300,300,'2026-10-08 10:42:28'),(14,9,'Lunch',1,500,500,'2026-10-08 10:42:40'),(15,9,'Tea / Coffee',1,150,150,'2026-10-08 10:42:48'),(16,9,'Dinner',1,600,600,'2026-10-08 10:43:03'),(17,9,'Laundry',38,250,9500,'2026-10-08 10:43:28'),(18,9,'Room Cleaning',19,200,3800,'2026-10-08 10:43:39'),(19,9,'Breakfast',18,300,5400,'2026-10-08 10:44:03'),(20,9,'Lunch',18,500,9000,'2026-10-08 10:44:14'),(21,9,'Dinner',18,600,10800,'2026-10-08 10:44:28'),(22,9,'Tea / Coffee',18,150,2700,'2026-10-08 10:44:38'),(23,10,'Breakfast',2,300,600,'2026-10-08 11:00:31'),(24,13,'Tea / Coffee',1,150,150,'2026-10-21 13:06:58'),(25,14,'Dinner',1,600,600,'2026-10-21 13:08:59'),(26,14,'Tea / Coffee',1,150,150,'2026-10-21 13:09:15'),(27,15,'Dinner',1,600,600,'2026-10-21 13:09:51'),(28,15,'Lunch',1,500,500,'2026-10-22 09:42:17'),(29,17,'Lunch',10,500,5000,'2026-10-22 09:46:23'),(30,17,'Breakfast',10,300,3000,'2026-10-22 09:46:41'),(31,17,'Dinner',10,600,6000,'2026-10-22 09:47:08'),(32,17,'Tea / Coffee',10,150,1500,'2026-10-22 09:47:14'),(33,17,'Extra Bed',10,800,8000,'2026-10-22 09:47:19'),(34,17,'Laundry',10,250,2500,'2026-10-22 09:47:24'),(35,17,'Room Cleaning',5,200,1000,'2026-10-22 09:47:44'),(36,19,'Laundry',1,250,250,'2026-11-07 11:40:57');
/*!40000 ALTER TABLE `room_services` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rooms`
--

DROP TABLE IF EXISTS `rooms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rooms` (
  `room_number` int NOT NULL,
  `floor_number` int NOT NULL,
  `room_type` varchar(50) NOT NULL,
  `price` double NOT NULL,
  `available` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`room_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rooms`
--

LOCK TABLES `rooms` WRITE;
/*!40000 ALTER TABLE `rooms` DISABLE KEYS */;
INSERT INTO `rooms` VALUES (101,1,'Single Bed',1000,1),(102,1,'Double Bed',2500,1),(103,1,'Deluxe Bed',4000,1),(201,2,'Single Bed',1000,1),(202,2,'Double Bed',2500,1),(203,2,'Deluxe Bed',4000,1),(301,3,'Single Bed',1000,1),(302,3,'Double Bed',2500,1),(303,3,'Deluxe Bed',4000,1),(401,4,'Single Bed',1000,1),(402,4,'Double Bed',2500,1),(403,4,'Deluxe Bed',4000,0),(501,5,'Single Bed',1000,0),(502,5,'Double Bed',2500,0),(503,5,'Deluxe Bed',4000,1),(601,6,'Single Bed',1000,1),(602,6,'Double Bed',2500,1),(603,6,'Deluxe Bed',4000,1),(701,7,'Single Bed',1000,1),(702,7,'Double Bed',2500,1),(703,7,'Deluxe Bed',4000,1),(801,8,'Single Bed',1000,1),(802,8,'Double Bed',2500,1),(803,8,'Deluxe Bed',4000,1);
/*!40000 ALTER TABLE `rooms` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `service_prices`
--

DROP TABLE IF EXISTS `service_prices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_prices` (
  `service_name` varchar(100) NOT NULL,
  `price` double NOT NULL,
  PRIMARY KEY (`service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `service_prices`
--

LOCK TABLES `service_prices` WRITE;
/*!40000 ALTER TABLE `service_prices` DISABLE KEYS */;
INSERT INTO `service_prices` VALUES ('Breakfast',300),('Dinner',600),('Extra Bed',800),('Laundry',250),('Lunch',500),('Room Cleaning',200),('Tea / Coffee',150);
/*!40000 ALTER TABLE `service_prices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'hotel_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-07 17:52:22
