/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.object.traversal;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.apache.tinkerpop.gremlin.object.reflect.Traversal.show;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.object.model.Object;
import org.apache.tinkerpop.gremlin.object.reflect.Parser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Column;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * The {@link ObjectQuery} implements the {@link Query} interface using the provided {@link
 * GraphTraversalSource}.
 *
 * <p>
 * Upon initialization, it gets a handle to the {@link GraphTraversalSource}. As traversal
 * functions, of various forms, are defined using the {@link #by} methods, it remembers them. When a
 * result is asked for through the {@link #one}, {@link #list}, or {@link #none} methods, it
 * constitutes a {@link GraphTraversal} using the given {@link SubTraversal}s. The resulting set of
 * property-based elements are mapped to corresponding object-based elements using the {@link
 * Parser#as} method, which does most of the heavy-lifting.
 *
 * @author Karthick Sankarachary (http://github.com/karthicks)
 */
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes", "PMD.AvoidDuplicateLiterals"})
public class ObjectQuery implements Query {

  @SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName"})
  private final GraphTraversalSource g;

  private AnyTraversal anyTraversal;
  private List<? extends SubTraversal> subTraversals;

  private List<StepType> disabledSteps = new ArrayList<>();
  private Selections selections;
  private Function<GraphTraversalSource, GraphTraversal> traversalFactory = g -> g.V();

  @Inject @Object
  public ObjectQuery(GraphTraversalSource g) {
    this.g = g;
  }

  @Override
  public void disable(StepType... disabledSteps) {
    this.disabledSteps = Arrays.asList(disabledSteps);
  }

  @Override
  @SuppressWarnings({"PMD.ShortMethodName"})
  public ObjectQuery by(AnyTraversal anyTraversal) {
    this.anyTraversal = anyTraversal;
    return this;
  }

  @Override
  @SuppressWarnings({"PMD.ShortMethodName"})
  public Query by(SubTraversal... subTraversals) {
    this.subTraversals = Arrays.asList(subTraversals);
    return this;
  }

  /**
   * Select one element of the given type, from the {@link #resultSet}.
   */
  @Override
  public <T> T one(Class<T> type) {
    try {
      List<T> resultSet = resultSet(g, type);
      if (resultSet == null || resultSet.isEmpty()) {
        throw Query.Exceptions.unexpectedMultipleResults(this);
      } else if (resultSet.size() > 1) {
        throw Query.Exceptions.unexpectedMultipleResults(this);
      }
      return resultSet.get(0);
    } finally {
      resetState();
    }
  }

  @Override
  public <T> Optional<T> optional(Class<T> type) {
    try {
      return Optional.of(one(type));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

    /**
   * Return the list of elements of the given type, using {@link #resultSet}.
   */
  @Override
  public <T> List<T> list(Class<T> type) {
    try {
      return resultSet(g, type);
    } finally {
      resetState();
    }
  }

  /**
   * Specify the element type for the alias referenced in a {@link GraphTraversal#select(Column)}.
   */
  @Override
  @SuppressWarnings({"PMD.ShortMethodName"})
  public Query as(String alias, Class elementType) {
    if (selections == null) {
      selections = Selections.of();
    }
    selections.as(alias, elementType);
    return this;
  }

  /**
   * Object a list of {@link org.apache.tinkerpop.gremlin.object.traversal.Selections.Selection}s,
   * one for each "row" in the {@link #resultSet}. From a selection, one can obtain the element
   * corresponding to the alias selected in the traversal.
   */
  @Override
  public Selections select() {
    try {
      return resultSet(g, selections);
    } finally {
      resetState();
    }
  }

  /**
   * Don't return anything, but do evaluate the {@link #resultSet}. This comes in handy when {@link
   * GraphTraversal#drop}s are involved.
   */
  @Override
  public void none() {
    try {
      resultSet(g, Object.class);
    } finally {
      resetState();
    }
  }

  @Override
  public Query source(Source source) {
    traversalFactory = source.get();
    return this;
  }

  @Override
  @SneakyThrows
  public void close() {
    if (g != null) {
      g.close();
    }
  }

  /**
   * For each row mapping returned by the {@link #resultSet}, convert the vertex/edge corresponding
   * to each {@link GraphTraversal#select}'ed alias, into corresponding object representations.
   */
  private Selections resultSet(GraphTraversalSource g, Selections selections) {
    Stream<Map<String, Object>> resultStream = (Stream<Map<String, Object>>) resultSet(g);
    List<Map<String, Object>> resultRows = resultStream.collect(Collectors.toList());
    for (Map<String, Object> resultRow : resultRows) {
      Selections.Selection selection = Selections.Selection.of();
      selections.add(selection);
      for (Map.Entry<String, Object> entry : resultRow.entrySet()) {
        String alias = entry.getKey();
        selection.put(alias, Parser.as(entry.getValue(), selections.as(alias)));
      }
    }
    return selections;
  }

  /**
   * Given the traversal source, generate the desired traversal. Then, given that the traversal is
   * already connected, do a {@code toBulkSet} to retrive the results. Finally, map each element in
   * the result to the desired type of element.
   */
  private <T> List<T> resultSet(GraphTraversalSource g, Class<T> elementType) {
    Stream<?> resultStream = resultSet(g);
    return resultStream
        .map(element -> Parser.as(element, elementType))
        .collect(Collectors.toList());
  }

  /**
   * Given a {@link GraphTraversalSource}, generate a {@link Stream} of results, by applying it on
   * the {@link #anyTraversal}, or {@link #subTraversals} provided by the {@link #by} method.
   */
  @SuppressWarnings("unchecked")
  private Stream<?> resultSet(GraphTraversalSource g) {
    Stream<?> resultStream;
    if (anyTraversal != null) {
      GraphTraversal traversal = anyTraversal.apply(g);
      if (hasDisabledSteps(traversal)) {
        throw Query.Exceptions.disabledStepQueried(disabledSteps);
      }
      long startTime = Clock.systemUTC().millis();
      resultStream = traversal.toBulkSet().stream();
      long endTime = Clock.systemUTC().millis();
      if (log.isDebugEnabled()) {
        log.debug("Executed chain of graph sub-traversals: {} in {}ms", show(traversal),
            endTime - startTime);
      }
    } else if (subTraversals != null) {
      GraphTraversal<Vertex, Vertex> traversal = traversalFactory.apply(g);
      for (SubTraversal subTraversal : subTraversals) {
        traversal = (GraphTraversal) subTraversal.apply(traversal);
      }
      if (hasDisabledSteps(traversal)) {
        throw Query.Exceptions.disabledStepQueried(disabledSteps);
      }
      long startTime = Clock.systemUTC().millis();
      resultStream = traversal.toBulkSet().stream();
      long endTime = Clock.systemUTC().millis();
      if (log.isDebugEnabled()) {
        log.debug("Executed chain of graph sub-traversals: {} in {}ms", show(traversal),
            endTime - startTime);
      }
    } else {
      throw Query.Exceptions.noTraversalSpecified(this);
    }
    return resultStream;
  }

  /**
   * Check if the traversal has any disabled steps.
   */
  private boolean hasDisabledSteps(GraphTraversal traversal) {
    return hasDisabledSteps(traversal.toString());
  }

  /**
   * For each disabled step, check if it appears in the given statement.
   */
  private boolean hasDisabledSteps(final String statement) {
    return !disabledSteps.isEmpty() && disabledSteps.stream().anyMatch(
        disabledStep -> containsIgnoreCase(statement, disabledStep.name()));
  }

  private void resetState() {
    this.source(Source.V);
    this.subTraversals = null;
    this.anyTraversal = null;
    this.selections = null;
  }
}
