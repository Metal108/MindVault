# MindVault 2.0 — Java 21

A compact CLI knowledge vault built with Java 21, Maven, JDBC and MySQL.

## Features
- Create notes with categories and multiple tags
- Search title, content, category and tags
- Browse notes and pin important ones
- Connect related notes
- Tasks with due dates and LOW/MEDIUM/HIGH priority
- In-app reminders for due-today and overdue tasks
- Knowledge review queue with a simple 7-day repeat interval
- Dashboard counts

## Setup
1. Run `database/schema.sql` in MySQL Workbench.
2. Edit `src/main/resources/db.properties` with your MySQL password.
3. Open the folder containing `pom.xml` in VS Code.
4. Run `mvn clean compile`, then `mvn exec:java`.

Requires JDK 21, Maven 3.9+ and MySQL 8+.

## Notifications
Reminders are in-app terminal notifications, not Windows push notifications. They are checked on startup and every 30 seconds while MindVault is open. The background checker uses a daemon `ScheduledExecutorService` thread. Each reminder is marked notified to avoid repeating it. Closing the app stops the checker.

## Knowledge review
To schedule an existing note for its first review, run in Workbench:
`UPDATE notes SET next_review = CURDATE() WHERE id = 1;`
After review, MindVault schedules it again in 7 days. This is a simple fixed interval, not a full spaced-repetition algorithm.

## Database tables
`notes`, `tags`, `note_tags`, `note_links`, `tasks`.

The project intentionally has only two Java source files: `Main.java` and `Database.java`. Note content is one line in this CLI edition.
