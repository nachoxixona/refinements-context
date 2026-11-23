/**
 * Ports of the microservice module that other modules implement (named interface, research R2). Not annotated as a
 * jMolecules port: the implementing module sees the call as inbound, so its implementation is a primary adapter
 * (contracts/module-apis.md, "SPI implementations are inbound adapters").
 */
@NamedInterface("spi")
package com.refinements.visualizer.microservice.spi;

import org.springframework.modulith.NamedInterface;
