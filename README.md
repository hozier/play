# play4s

![TLS Enabled](https://img.shields.io/badge/TLS-enabled-brightgreen?style=flat-square&logo=letsencrypt&logoColor=white)


###### (a) Introduction: From Puzzle Solving to Platform — A Functional and Secure Compute Layer

`play4s-service` and its supporting modules are built atop [cats-effect](https://github.com/typelevel/cats-effect) and
[fs2](https://github.com/functional-streams-for-scala/fs2), fully embracing the foundational principles of functional programming. This design emphasizes purity, composability, and effect management—enabling precise control over resource lifecycles, concurrency, and asynchronous computations in a principled, type-safe manner.

What began as an exploration into puzzle-solving—specifically Sudoku—has progressively transformed into a more expansive and versatile compute platform. Core algorithms have been rearchitected through a functional-first lens, enabling advanced features such as parallel solver execution during compute requests. A prime example is the constraint propagation algorithm, which drastically reduces the search space to accelerate resolution. Leveraging the concurrency model offered by Cats Effect, we can benchmark this solver by racing it against an alternative implementation adhering to the same trait—automatically short-circuiting as soon as the quicker solution emerges. This not only demonstrates tangible performance improvements but also guarantees timely and accurate results.

This technical evolution represents a key inflection point: solver logic has been modularized into composable, service-oriented units, forming a scalable foundation for future expansion. These services now underpin a robust, high-performance computation layer that is both adaptable and maintainable.

Complementing this architecture is a hardened authentication stack that blends API key and OTP-based verification, JWT token lifecycle governance, and secure credential storage. Together, these safeguards ensure an uncompromising streamlined, secure interaction model—for both consumers of the service and the developers who extend it.


###### (b) Performance Progress and Example Analytics

The performance of the backtracking algorithm has improved significantly over time, with optimizations and a shift to a functional approach.

###### **v1: Initial Implementation**

- **May 12, 2016**: The total runtime was **461 seconds** for solving multiple puzzles (20 puzzles of 9x9, one 16x16, one 5x5, and one 4x4). The average runtime per board was approximately **23 seconds**.
- **May 14, 2016**: Through optimization efforts, the total runtime was reduced to **15 seconds**, with the average runtime per board dropping to around **0.68 seconds**.

**Analytics (v1)**

**9x9 Sudoku Example**

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

Algorithm: **Backtracking**  
Elapsed Time: **0.316678 seconds**


###### **v2: Functional Approach**

The functional approach further improved the performance of the backtracking algorithm. Below is an example of a compute request and its corresponding summary.

**Compute Request:**
```
5 . 6 . . 3 1 9 .
8 4 1 . 5 6 . 2 7
. . . 1 7 . . 6 .
. 6 . 5 1 9 . 4 8
1 . 4 2 3 7 9 . 6
9 2 . 6 4 8 . 3 .
. 1 . . 6 2 . . .
7 5 . 4 9 . 6 8 3
. 3 9 7 . . 4 . 2
```

**Sudoku Computation Summary:**
```
{
    "id": "a2c9acc3-0cb7-4209-97f7-02e286bc7c48",
    "duration": 7,
    "requestedAt": "2025-04-12T23:22:31.358457Z",
    "concurrentExecutionDetails": {
        "strategies": [
            "backtracking",
            "constraintPropagation"
        ],
        "earliestCompleted": "constraintPropagation"
    },
    "solution": {
        "value": [
            [5,7,6,8,2,3,1,9,4],
            [8,4,1,9,5,6,3,2,7],
            [2,9,3,1,7,4,8,6,5],
            [3,6,7,5,1,9,2,4,8],
            [1,8,4,2,3,7,9,5,6],
            [9,2,5,6,4,8,7,3,1],
            [4,1,8,3,6,2,5,7,9],
            [7,5,2,4,9,1,6,8,3],
            [6,3,9,7,8,5,4,1,2]
        ]
    }
}
```

###### (c) Endpoints

**Load Balancer Endpoint:**

| Environment | Load Balancer URL                                         |
| ----------- | --------------------------------------------------------- |
| PROD        | `app-pl-LoadB-g8oJhj03r5OI-1073683086.us-east-1.elb.amazonaws.com` |

To retrieve the latest Load Balancer endpoint, re-trigger the GitHub Actions workflow `Deploy to AWS`. The updated endpoint will appear under the `Deploy` job in the `Query Load Balancer DNS Name` step.

**API Endpoints:**

| Endpoint Path                         | Description                     |
| ------------------------------------- | ------------------------------- |
| `/internal/auth/token`                | JWT generation endpoint         |
| `/internal/auth/otp/initiate`         | OTP initiation endpoint         |
| `/internal/auth/otp/authorize`        | OTP authorization endpoint      |
| `/internal/meta/health`               | Health check endpoint           |
| `/internal/meta/version`              | Runtime build details endpoint  |
| `/internal/game/sudoku/hints`         | Generate playable cell hints    |
| `/internal/game/sudoku/metrics`       | Retrieve metrics about computations |
| `/internal/game/sudoku/solve`         | Developer endpoint              |
| `/public/game/sudoku/solve`           | Sudoku puzzle-solving endpoint  |

###### (d) Documentation

`Smithy4s` automatically generates an OpenAPI view for this service. By default, the documentation is available at the `/docs` path.

###### (e) Environment Variables

| **Category**                  | **Environment Variable**         | **Description**                                              |
|-------------------------------|-----------------------------------|--------------------------------------------------------------|
| **GitHub Actions Workflows**  | `AWS_ACCOUNT_ID`                 | The AWS account ID used for deployment.                     |
|                               | `GIT_SHA`                        | The Git commit SHA used to tag builds or track changes.      |
|                               | `REGISTRY`                       | The container registry URL (e.g., AWS ECR, Docker Hub).      |
|                               | `REPOSITORY`                     | The name of the repository in the container registry.        |
|                               | `IMAGE_TAG`                      | The tag assigned to the container image (e.g., latest, GIT_SHA). |
|                               | `AWS_ACCESS_KEY_ID`              | AWS access key for authentication.                          |
|                               | `AWS_SECRET_ACCESS_KEY`          | AWS secret access key for authentication.                   |
| **Infrastructure/Application**| `GOOGLE_CLOUD_API_KEY_BASE64`    | Base64-encoded service account key for Google Cloud API access.         |
|                               | `GOOGLE_APPLICATION_CREDENTIALS` | The file path to the Google Cloud service account credentials. |
|                               | `KEYSTORE_BASE64`                | Base64-encoded keystore for TLS.                             |
|                               | `KEYSTORE_PASSWORD_BASE64`       | Base64-encoded password for the keystore.                   |
|                               | `PLAY4S_API_KEY_BASE64`          | Base64-encoded API key for the application.                 |
|                               | `KEYSTORE_CREDENTIALS`           | The file path to the keystore file.                         |
|                               | `APP_NAME`                       | The name of the application (e.g., play4s).                 |
|                               | `SERVICE_BIND_HOST`              | The host address the service binds to (e.g., localhost).    |
|                               | `SERVICE_BIND_PORT`              | The port the service binds to (e.g., 8080).                 |
|                               | `IMAGE_DIGEST`                   | The image digest for the container.                         |

###### (f) Commands Reference

**Run the application:**
```shell
sbt clean reload compile app/run
```

**Run tests:**
```shell
sbt test
```

**Build Docker Images:**
```shell
sbt app/docker:publishLocal
```

**Run Docker Images:**
```shell
# Refer to section (e) under Infrastructure/Application for the 
# required environment variables needed to configure the .env file.
docker run --platform linux/amd64 --env-file .env -p 8080:8080 \
    -v /tmp/secrets:/tmp/secrets \
    --user root \
```

###### (g) Generating Test Image Data

To generate test image data safely, follow these steps:

1. Create a base64 string:
```shell
base64 <path-to-image> | tr -d '\r\n' > image.txt
```

2. Create a JSON payload:
```shell
jq -n --arg img "$(cat image.txt)" '{image:$img}' > payload.json
```

3. Validate the JSON:
```shell
cat payload.json
```

4. Send the request:
```shell
curl -X POST https://localhost:8080/public/game/sudoku/solve \
-H "Content-Type: application/json" \
-d @payload.json
```

###### (h) Cloud Vision API by Google Cloud

The Cloud Vision API enables features like image labeling, OCR, and more. This project uses the API to extract insights from game board images.

###### (i) Scala Compiler Configuration (sbt-tpolecat)

This project uses the [`sbt-tpolecat`](https://github.com/typelevel/sbt-tpolecat/) plugin to enforce recommended Scala compiler options. For more details, refer to the [official documentation](https://github.com/typelevel/sbt-tpolecat/).

