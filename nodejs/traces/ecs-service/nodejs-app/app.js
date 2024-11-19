// app.js

const express = require('express');
const { startTracing } = require('./tracing');

// Start tracing before any other code
startTracing();

// Import the OpenTelemetry API
const { trace } = require('@opentelemetry/api');

// Get a tracer
const tracer = trace.getTracer('nodejs-app');

const app = express();
const port = 3000;

// Middleware to create a root span for each request
app.use((req, res, next) => {
  const span = tracer.startSpan(`HTTP ${req.method} ${req.path}`);
  // Attach the span to the request object so we can use it in routes
  req.span = span;
  // Ensure the span ends when the response is finished
  res.on('finish', () => {
    span.end();
  });
  next();
});

app.get('/', (req, res) => {
  // Use the span from the middleware
  const span = req.span;
  span.addEvent('Handling / request');
  res.send('Hello from the instrumented Node.js app!');
});

app.get('/hello', (req, res) => {
  const span = req.span;
  span.addEvent('Handling /hello request');
  const name = req.query.name || 'World';

  // Start a child span for some operation (e.g., processing data)
  const childSpan = tracer.startSpan('processData', {
    parent: span,
  });
  // Simulate some processing
  childSpan.addEvent('Processing data');
  // ... your processing logic here
  childSpan.end();

  res.send(`Hello, ${name}!`);
});

app.listen(port, () => {
  console.log(`Node.js app listening at http://localhost:${port}`);
});
