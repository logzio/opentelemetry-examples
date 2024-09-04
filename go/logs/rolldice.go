package main

import (
	"go.opentelemetry.io/contrib/bridges/otelslog"
)

const name = "go.opentelemetry.io/otel/example/dice"

var (
	logger = otelslog.NewLogger(name)
)
