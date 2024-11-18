  // dice.js
function rollOnce(min, max) {
  return Math.floor(Math.random() * (max - min + 1) + min);
}

function rollTheDice(rolls, min, max) {
  const results = [];
  for (let i = 0; i < rolls; i++) {
    results.push(rollOnce(min, max));
  }
  return results;
}

module.exports = { rollTheDice };  