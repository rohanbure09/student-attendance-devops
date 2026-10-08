# Student Attendance System (Java)

A student attendance web app written in plain Java 17+ using only the JDK (built-in web server, no Maven, no Gradle, no downloads), packaged for DevOps practice: Docker, CI/CD and Kubernetes.

## Features
- Add / delete students
- Mark Present / Absent for any date (re-saving a date updates it)
- Dashboard with today's counts
- Attendance report with percentage per student (below 75% highlighted)
- `/health` endpoint and `/api/students` JSON endpoint
- Data is saved to a file (`data/attendance-data.tsv`), so it survives restarts

## Project structure
```
attendance-system-java/
├── src/main/java/attendance/   # application code
│   ├── Main.java               # entry point
│   ├── AttendanceServer.java   # HTTP routes
│   ├── Store.java              # data + file persistence
│   ├── Pages.java, Html.java   # HTML pages and CSS
│   └── HealthCheck.java        # used by the Docker HEALTHCHECK
├── src/test/java/attendance/TestRunner.java   # 11 automated tests
├── build.sh / build.bat        # compile + package out/attendance.jar
├── test.sh  / test.bat         # build + run the tests
├── run.sh   / run.bat          # start the app
├── Dockerfile, docker-compose.yml
├── Jenkinsfile                 # Jenkins pipeline
├── .github/workflows/ci.yml    # GitHub Actions pipeline
└── k8s/deployment.yaml         # Kubernetes Deployment + Service
```

## Requirements
JDK 17 or newer. Check with `java -version` and `javac -version` (both must work and be 17+).

## 1. Run locally (no Docker)
Mac / Linux:
```bash
bash build.sh
bash run.sh
```
Windows (Command Prompt):
```bat
build.bat
run.bat
```
Open http://localhost:8080 and stop with `Ctrl + C`.

## 2. Run the tests
```bash
bash test.sh        # Windows: test.bat
```
Expected last line: `11 passed, 0 failed`.

## 3. Run with Docker
```bash
docker build -t attendance-system-java .
docker run -d -p 8080:8080 -v attendance-data:/data --name attendance-system-java attendance-system-java
```

## 4. Run with Docker Compose
```bash
docker compose up -d --build
```
Open http://localhost:8080. Stop with `docker compose down`.

## 5. CI/CD
- **GitHub Actions**: push to GitHub (branch `main`); `.github/workflows/ci.yml` runs the tests, builds the image and smoke-tests it.
- **Jenkins**: create a Pipeline job pointing at this repo; it uses the `Jenkinsfile` (agent needs JDK 17+ and Docker).

## 6. Kubernetes (minikube / kind / Docker Desktop)
```bash
docker build -t attendance-system-java:latest .
# minikube only: minikube image load attendance-system-java:latest
# kind only:     kind load docker-image attendance-system-java:latest
kubectl apply -f k8s/deployment.yaml
kubectl get pods
```
Open http://localhost:30081 (minikube: `minikube service attendance-system-java`).

Note: the manifest uses `emptyDir`, so data resets when the pod restarts. Swap in a PersistentVolumeClaim for persistence.

## Environment variables
| Name | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Port the web server listens on |
| `DATA_DIR` | `data` | Folder where `attendance-data.tsv` is stored |
