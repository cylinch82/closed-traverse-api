# Closed Traverse API

Java 21 API for one closed-traverse survey at a time. It validates ordered observations, allocates coordinate closure error in proportion to each observed horizontal distance, and saves the original observations with the calculated results. The calculation follows [NOTES_01.md](NOTES_01.md).

## Run

Use the project's `backend` conda environment, which provides Java 21. If the environment does not yet exist, create it with `conda env create -f environment.yaml`. Maven Wrapper downloads Maven on its first use.

```sh
conda run -n backend ./mvnw test
bash scripts/test-gitignore.sh
conda run -n backend ./mvnw spring-boot:run
```

The server listens on `http://localhost:8080`. H2 saves data in `data/closed-traverse.mv.db`; the generated database file is ignored by Git. To build a standalone jar, run `conda run -n backend ./mvnw package` and then `conda run -n backend java -jar target/closed-traverse-api-0.1.0.jar`.

## Web demo

Keep the `spring-boot:run` terminal open, then visit [http://localhost:8080/](http://localhost:8080/) in a browser. The page starts with an **unsaved** example loop. Edit its legs or clear the form, submit to calculate and save, and use the history section to reopen saved traverses. The web page and API are served by the same Spring Boot process; no separate frontend server is needed. If the browser says localhost refused the connection, start the server and wait for the `Tomcat started on port 8080` message. Stop it with Ctrl+C after the demo.

## API

Submit a complete loop in travel order:

```sh
curl -i -X POST http://localhost:8080/traverses \
  -H 'Content-Type: application/json' \
  -d '{
    "timeZone": "Asia/Taipei",
    "legs": [
      {"fromStationNo":"S1","toStationNo":"S2","distanceM":30.004,"azimuthDeg":89.98},
      {"fromStationNo":"S2","toStationNo":"S3","distanceM":20.006,"azimuthDeg":0.03},
      {"fromStationNo":"S3","toStationNo":"S4","distanceM":29.991,"azimuthDeg":269.97},
      {"fromStationNo":"S4","toStationNo":"S1","distanceM":19.985,"azimuthDeg":180.02}
    ]
  }'
```

`POST /traverses` responds with `201 Created`, an `id`, and a `Location` header. `GET /traverses/{id}` returns the time zone, system creation time, algorithm version, total observed length, original X/Y closure components, and every leg's original observations, raw increments, corrections, adjusted increments, and adjusted end coordinates. `GET /traverses` returns summaries in descending `createdAt` order. Invalid input returns `400`; an unknown ID returns `404`.

Input requires at least three legs; station IDs must contain 1 to 100 characters; consecutive legs must connect and the last must return to the first station. Distances must be positive finite numbers, and azimuths must be finite degrees in `[0, 360)`. The time zone must contain 1 to 100 characters and be recognized by Java's `ZoneId`. The server checks format and calculability, not survey quality: a large closure error is still saved and returned for inspection.

## Calculation and storage

X points east, Y points north, and azimuth is clockwise from north. The first station is fixed at `(0, 0)`. For each leg, raw increments are `L × sin(θ)` and `L × cos(θ)`; its corrections are `−fX × L/P` and `−fY × L/P`, where `P` is the sum of original distances. The API calculates in `double` without display rounding. Coordinates are local and relative, not map coordinates. Original distance and azimuth remain unchanged. `createdAt` is record creation time, not measurement time; `timeZone` alone does not specify when the survey happened.

The H2 database has `traverse` and `traverse_leg` tables. Creation uses a single transaction, so a failed insert leaves no partial record. `adjustmentVersion` identifies the algorithm used for saved results. The database and response rounding policy can be revisited when requirements are settled. This implementation does not perform angle adjustment, precision grading, or a legal cadastral survey certification.
