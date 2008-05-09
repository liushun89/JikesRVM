/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.stickyms;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements the thread-local functionality for a transitive
 * closure over a mark-sweep space.
 */
@Uninterruptible
public final class StickyMSNurseryTraceLocal extends TraceLocal {

  /****************************************************************************
  *
  * Instance fields.
  */
 private final ObjectReferenceDeque modBuffer;

  /**
   * Constructor
   */
  public StickyMSNurseryTraceLocal(Trace trace, ObjectReferenceDeque modBuffer) {
    super(StickyMS.SCAN_NURSERY, trace);
    this.modBuffer = modBuffer;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(StickyMS.MARK_SWEEP, object))
      return StickyMS.msSpace.isLive(object);
    else if (StickyMS.NURSERY_COLLECT_PLOS && Space.isInSpace(StickyMS.PLOS, object))
      return StickyMS.ploSpace.isLive(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(super.isLive(object));
    return true;
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * In this instance, we refer objects in the mark-sweep space to the
   * msSpace for tracing, and defer to the superclass for all others.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(StickyMS.MARK_SWEEP, object))
      return StickyMS.msSpace.traceObject(this, object);
    else if (StickyMS.NURSERY_COLLECT_PLOS && Space.isInSpace(StickyMS.PLOS, object))
      return StickyMS.ploSpace.traceObject(this, object);
    else
      return object;
  }

  /**
   * Process any remembered set entries.  This means enumerating the
   * mod buffer and for each entry, marking the object as unlogged
   * and enqueing it for scanning.
   */
  protected void processRememberedSets() {
    logMessage(2, "processing modBuffer");
    while (!modBuffer.isEmpty()) {
      ObjectReference src = modBuffer.pop();
      Plan.markAsUnlogged(src);
      processNode(src);
    }
  }
}
