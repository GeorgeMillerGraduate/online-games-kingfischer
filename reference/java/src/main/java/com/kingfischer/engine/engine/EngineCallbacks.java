// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.engine;

/**
 * Output boundary. A future GUI can consume UCI lines or supply its own
 * listener.
 */
@FunctionalInterface
public interface EngineCallbacks {

    void line(String line);
}
