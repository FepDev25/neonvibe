# Commands

- ./backend/mvnw -f backend/pom.xml test
- pnpm -C frontend test
- ./backend/mvnw -f backend/pom.xml package -DskipTests
- pnpm -C frontend build
- ALLOW_NO_GOOGLE=1 ./deploy/build.sh
