const express = require('express');
const { logger } = require('./logger/logger.js');
const { rollTheDice } = require('./lib/dice.js');

const app = express();
const PORT = process.env.PORT || 8080;

app.get('/rolldice', (req, res) => {
    const rolls = req.query.rolls ? parseInt(req.query.rolls.toString()) : NaN;
    if (isNaN(rolls)) {
      logger.emit({
        body: "Invalid request parameter for rolls",
        attributes: { severityText: 'error' },
      });
      res.status(400).send("Request parameter 'rolls' is missing or not a number.");
      return;
    }
    const result = rollTheDice(rolls, 1, 6);
    logger.emit({
        body: `Dice rolled: ${result}`,
        attributes: { severityText: 'info' },
    });
    res.send(JSON.stringify(result));
});

  
app.listen(PORT, () => {
console.log(`Listening for requests on http://localhost:${PORT}`);
});
