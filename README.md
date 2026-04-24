# Quiz Leaderboard System

This project is my solution for the Bajaj Finserv Health JAVA Qualifier assignment (SRM, April 2026).

## What it does

The program connects to a quiz validator API and fetches quiz scores for multiple participants. Since the API can return the same data in different polls, the program handles that by removing duplicates before calculating the final scores.

Here's the basic flow:
1. Calls the GET API 10 times (poll 0 to 9), with a 5 second gap between each call
2. Collects all the score events from every poll response
3. Removes duplicate entries — if the same (roundId + participant) pair shows up more than once, only the first occurrence is counted
4. Adds up scores for each participant
5. Sorts everyone by their total score (highest first)
6. Sends the final leaderboard to the POST API

## How to run

You need Java 11 or higher installed.

```
cd quiz-leaderboard
javac src/QuizLeaderboard.java -d out
java -cp out QuizLeaderboard
```

The program will take about 50 seconds to run because of the 5 second delays between polls.

## How the deduplication works

The API might send the same event in multiple polls. For example, "Alice scored 10 in Round 1" could show up in poll 0 and again in poll 3. If we just add them up blindly, we'd count Alice's score twice.

To fix this, I use a `HashSet` with a key made from `roundId_participant`. Before adding any event's score, I check if we've already seen that combination. If yes, I skip it.

## Tech used

- Plain Java (no frameworks, no Maven/Gradle)
- `java.net.http.HttpClient` for API calls
- Manual JSON parsing (no external libraries needed)

## Project structure

```
quiz-leaderboard/
├── src/
│   └── QuizLeaderboard.java
├── .gitignore
└── README.md
```

## API details

- **GET** `/quiz/messages?regNo=RA2311003010551&poll={0-9}` — fetches quiz events
- **POST** `/quiz/submit` — submits the final leaderboard

Base URL: `https://devapigw.vidalhealthtpa.com/srm-quiz-task`
