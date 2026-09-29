package com.closedtraverse.service;

import java.util.UUID;

public class TraverseNotFoundException extends RuntimeException {
    public TraverseNotFoundException(UUID id) {
        super("Traverse " + id + " was not found.");
    }
}
