# Use Node.js LTS version
FROM node:16-alpine

# Set the working directory
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm install --production

# Copy the application code
COPY . .

# Expose the application port
EXPOSE 3000

# Set environment variables for OpenTelemetry
ENV OTEL_TRACES_SAMPLER=always_on
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
ENV OTEL_RESOURCE_ATTRIBUTES="service.name=nodejs-app"

# Start the application
CMD ["npm", "start"]
