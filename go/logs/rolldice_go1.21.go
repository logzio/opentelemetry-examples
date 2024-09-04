package main

import (
	"io"
	"math/rand"
	"net/http"
	"strconv"
	"strings"
)

func rolldice(w http.ResponseWriter, r *http.Request) {
	// Extract the player's name from a query parameter or other parts of the request
	path := r.URL.Path
	segments := strings.Split(path, "/")
	playerName := "Anonymous" // Default name if not specified

	if len(segments) > 2 && segments[2] != "" {
		playerName = segments[2]
	}

	roll := 1 + rand.Intn(6)

	// Logging the result
	if playerName == "Anonymous" {
		logger.Info("Anonymous player is rolling the dice", "result", roll)
	} else {
		logger.Info(playerName+" is rolling the dice", "result", roll)
	}

	// Sending the response back to the client
	resp := strconv.Itoa(roll) + "\n"
	if _, err := io.WriteString(w, resp); err != nil {
		logger.Error("Write failed", "error", err)
	}
}
