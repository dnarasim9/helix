package org.apache.helix.task;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Provides a convenient way to construct, traverse,
 * and validate a task dependency graph
 */
public class TaskDag {
  @JsonProperty("parentsToChildren")
  private final Map<String, Set<String>> _parentsToChildren;

  @JsonProperty("childrenToParents")
  private final Map<String, Set<String>> _childrenToParents;

  @JsonProperty("allNodes")
  private final Set<String> _allNodes;

  public static final TaskDag EMPTY_DAG = new TaskDag();

  public TaskDag() {
    _parentsToChildren = new TreeMap<String, Set<String>>();
    _childrenToParents = new TreeMap<String, Set<String>>();
    _allNodes = new TreeSet<String>();
  }

  public void addParentToChild(String parent, String child) {
    if (!_parentsToChildren.containsKey(parent)) {
      _parentsToChildren.put(parent, new TreeSet<String>());
    }
    _parentsToChildren.get(parent).add(child);

    if (!_childrenToParents.containsKey(child)) {
      _childrenToParents.put(child, new TreeSet<String>());
    }
    _childrenToParents.get(child).add(parent);

    _allNodes.add(parent);
    _allNodes.add(child);
  }

  public void addNode(String node) {
    _allNodes.add(node);
  }

  public Map<String, Set<String>> getParentsToChildren() {
    return _parentsToChildren;
  }

  public Map<String, Set<String>> getChildrenToParents() {
    return _childrenToParents;
  }

  public Set<String> getAllNodes() {
    return _allNodes;
  }

  public Set<String> getDirectChildren(String node) {
    if (!_parentsToChildren.containsKey(node)) {
      return Collections.emptySet();
    }
    return _parentsToChildren.get(node);
  }

  public Set<String> getDirectParents(String node) {
    if (!_childrenToParents.containsKey(node)) {
      return Collections.emptySet();
    }
    return _childrenToParents.get(node);
  }

  public String toJson() throws Exception {
    return new ObjectMapper().writeValueAsString(this);
  }

  public static TaskDag fromJson(String json) {
    try {
      return new ObjectMapper().readValue(json, TaskDag.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse json " + json + " into task dag");
    }
  }

  /**
   * Checks that dag contains no cycles and all nodes are reachable.
   */
  public void validate() {
    Set<String> prevIteration = new TreeSet<String>();

    // get all unparented nodes
    for (String node : _allNodes) {
      if (getDirectParents(node).isEmpty()) {
        prevIteration.add(node);
      }
    }

    // visit children nodes up to max iteration count, by which point we should have exited
    // naturally
    Set<String> allNodesReached = new TreeSet<String>();
    int iterationCount = 0;
    int maxIterations = _allNodes.size() + 1;

    while (!prevIteration.isEmpty() && iterationCount < maxIterations) {
      // construct set of all children reachable from prev iteration
      Set<String> thisIteration = new TreeSet<String>();
      for (String node : prevIteration) {
        thisIteration.addAll(getDirectChildren(node));
      }

      allNodesReached.addAll(prevIteration);
      prevIteration = thisIteration;
      iterationCount++;
    }

    allNodesReached.addAll(prevIteration);

    if (iterationCount >= maxIterations) {
      throw new IllegalArgumentException("DAG invalid: cycles detected");
    }

    if (!allNodesReached.containsAll(_allNodes)) {
      throw new IllegalArgumentException("DAG invalid: unreachable nodes found. Reachable set is "
          + allNodesReached);
    }
  }
}
