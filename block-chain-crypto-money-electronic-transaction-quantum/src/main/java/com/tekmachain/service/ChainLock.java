package com.tekmachain.service;

import org.springframework.stereotype.Component;

/**
 * A single shared monitor for every operation that mutates the chain — mining,
 * appending a peer's block, and adopting a longer chain. Making them all
 * synchronize on the same object guarantees one writer at a time.
 */
@Component
public class ChainLock {
}
