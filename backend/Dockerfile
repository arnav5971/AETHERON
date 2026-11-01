FROM eclipse-temurin:17-jre
WORKDIR /app
COPY backend/build/install/backend/ ./
ENV PORT=8080
EXPOSE 8080
CMD ["bin/backend"]
