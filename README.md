# play4s

###### (a) Current Development

This project explores various puzzle-solving algorithms, with a current emphasis on Sudoku puzzles. One of the main algorithms implemented is the backtracking algorithm. All compute solutions are now accessible as a service as of latest refactor.

###### (b.1) Performance Progress: v1

Initially, the backtracking algorithm had significant runtime:

- **May 12, 2016**: Total runtime was **461 seconds** for solving multiple puzzles (20 puzzles of 9x9, one 16x16, one 5x5, one 4x4). The average runtime per board was approximately **23 seconds**.

With optimization, runtime drastically improved:

- **May 14, 2016**: Reduced total runtime to **15 seconds**, bringing the average runtime per board down to around **0.68 seconds**.
###### (b.2) Performance Progress: v2 (Functional Approach)
Compute request (_Content-Type: multipart/form-data_):
```
5 3 . . 7 . . . .
6 . . 1 9 5 . . .
. 9 8 . . . . 6 .
8 . . . 6 . . . 3
4 . . 8 . 3 . . 1
7 . . . 2 . . . 6
. 6 . . . . 2 8 .
. . . 4 1 9 . . 5
. . . . 8 . . 7 9
```

Solution:
```
5 3 4 6 7 8 9 1 2
6 7 2 1 9 5 3 4 8
1 9 8 3 4 2 5 6 7
8 5 9 7 6 1 4 2 3
4 2 6 8 5 3 7 9 1
7 1 3 9 2 4 8 5 6
9 6 1 5 3 7 2 8 4
2 8 7 4 1 9 6 3 5
3 4 5 2 8 6 1 7 9
```

Sudoku Computation Summary:
```
{
    "id": "c2244951-e297-41d1-abff-fbc59b803e41",
    "strategy": "com.theproductcollectiveco.play4s.game.sudoku.core.BacktrackingAlgorithm$$anon$1",
    "duration": 2298,
    "requestedAt": {
        "epochSecond": 1742916339,
        "nano": 587805000
    },
    "solution": {
        "value": [
          ...
    }
}
```

###### (c) How to Run

See section (e).

###### (d.1) Example Puzzle Analytics: v1

###### 9x9 Sudoku Example

**Puzzle:**
```
. 1 . . 2 . 3 . .
. . 2 . . 3 . 4 .
. 5 . . . . . . 6
. . 7 8 . . . 5 .
. . . 1 . . . . 4
. 8 . . 9 4 . . .
3 . . . . 7 . 9 .
. . . 4 . . 1 . 5
. . 6 . . . . . .
```

**Solution:**
```
6 1 4 5 2 9 3 8 7
8 7 2 6 1 3 5 4 9
9 5 3 7 4 8 2 1 6
4 3 7 8 6 2 9 5 1
2 6 9 1 7 5 8 3 4
1 8 5 3 9 4 7 6 2
3 4 1 2 5 7 6 9 8
7 9 8 4 3 6 1 2 5
5 2 6 9 8 1 4 7 3
```

Algorithm used: **Backtracking**  
Elapsed time: **0.316678 seconds**

###### 5x5 Sudoku Example

**Puzzle:**
```
. 1 . . 2
. 3 . . .
. 4 . . 3
. 5 . . 1
. 2 . . .
```

**Solution:**
```
4 1 3 5 2
5 3 1 2 4
2 4 5 1 3
3 5 2 4 1
1 2 4 3 5
```

Algorithm used: **Backtracking**  
Elapsed time: **0.002222 seconds**

###### 4x4 Sudoku Example

**Puzzle:**
```
4 . 1 .
1 2 . .
. 1 . .
. 4 . 1
```

**Solution:**
```
4 3 1 2
1 2 3 4
2 1 4 3
3 4 2 1
```

Algorithm used: **Backtracking**  
Elapsed time: **0.000629 seconds**

###### (d.2) Example Puzzle Analytics: v2 (Functional Approach)
See (b.2).

###### (e) Commands Reference

Once inside the `sbt` shell, run the following commands:

**Run the application:**

```shell
project app; run
```

**Run tests:**

```shell
test
```

**Docker Compose Setup:**

Build Docker images:
```shell
docker-compose build
```

Run for active Scala development (interactive mode):
```shell
docker-compose up app
```

Run in production mode:
```shell
docker-compose up app-prod
```

###### (f.1) Generating new test image data

To guarantee correctness, we always safely generate the payload with a JSON-aware tool like `jq`.

Run:

```shell
# Step 1 Safely create base64 string without newlines:
base64 <fully-qualified-or-relative-path-to-test-image> | tr -d '\r\n' > image.txt
```

```shell
# Step 2 (Safe JSON payload creation):
jq -n --arg img "$(cat image.txt)" '{image:$img}' > payload.json
```

Why use `jq`?

`jq` automatically escapes special characters (e.g., quotes, $) safely.

Ensures 100% valid JSON.

🟢 Confirm JSON validity:
```shell
cat payload.json
```

Then POST your request, either with `curl`:

```shell
curl -X POST localhost:8080/game/sudoku/solve \
-H "Content-Type: application/json" \
-d @payload.json
````

or copy the verified content into Postman exactly as-is.

##### (g) Cloud Vision API by Google Cloud

"Cloud Vision API allows developers to easily integrate vision detection features within applications, including image labeling, face and landmark detection, optical character recognition (OCR), and tagging of explicit content." We internally call out to the API to extract insights from game board images.

###### (h) Scala Compiler Configuration (sbt-tpolecat)

This project leverages the [`sbt-tpolecat`](https://github.com/typelevel/sbt-tpolecat/) plugin to apply recommended Scala compiler options. For customization options or to explore other available plugin modes, refer to the [official documentation](https://github.com/typelevel/sbt-tpolecat/).

