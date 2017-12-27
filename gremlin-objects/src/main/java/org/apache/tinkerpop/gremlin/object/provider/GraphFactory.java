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
package org.apache.tinkerpop.gremlin.object.provider;

import org.apache.tinkerpop.gremlin.object.structure.Graph;
import org.apache.tinkerpop.gremlin.object.structure.ObjectGraph;
import org.apache.tinkerpop.gremlin.object.traversal.ObjectQuery;
import org.apache.tinkerpop.gremlin.object.traversal.Query;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import lombok.RequiredArgsConstructor;

/**
 * The {@link GraphFactory} provided instances of the {@link Graph} and {@link Query } that may
 * be optionally cached, as specified by the {@link #shouldCache} value.
 *
 * @author Karthick Sankarachary (http://github.com/karthicks)
 */
@RequiredArgsConstructor(staticName = "of")
public class GraphFactory {

  protected final GraphTraversalSource g;

  protected final ShouldCache shouldCache;

  private static Graph cachedGraph;
  private static Query cachedQuery;

  private static ThreadLocal<Graph> threadGraph = new ThreadLocal<>();
  private static ThreadLocal<Query> threadQuery = new ThreadLocal<>();

  @SuppressWarnings("PMD.ShortMethodName")
  public static GraphFactory of(GraphTraversalSource g) {
    return of(g, ShouldCache.THREAD_LOCAL);
  }

  protected Query makeQuery() {
    return new ObjectQuery(g);
  }

  protected Graph makeGraph() {
    return new ObjectGraph(g, query());
  }

  public final Graph graph() {
    switch (shouldCache) {
      case EVERYTHING:
        if (cachedGraph != null) {
          return cachedGraph;
        }
        break;
      case THREAD_LOCAL:
        if (threadGraph.get() != null) {
          return threadGraph.get();
        }
        break;
      case NOTHING:
        return makeGraph();
      default:
        break;
    }
    synchronized (GraphFactory.class) {
      if (cachedGraph != null) {
        cachedGraph.reset();
        return cachedGraph;
      }
      Graph graph = makeGraph();
      switch (shouldCache) {
        case EVERYTHING:
          cachedGraph = graph;
          break;
        case THREAD_LOCAL:
          threadGraph.set(graph);
          break;
        case NOTHING:
        default:
      }
      graph.reset();
      return graph;
    }
  }

  public final Query query() {
    switch (shouldCache) {
      case EVERYTHING:
        if (cachedQuery != null) {
          return cachedQuery;
        }
        break;
      case THREAD_LOCAL:
        if (threadQuery.get() != null) {
          return threadQuery.get();
        }
        break;
      case NOTHING:
        return makeQuery();
      default:
        break;
    }
    synchronized (GraphFactory.class) {
      if (cachedQuery != null) {
        return cachedQuery;
      }
      Query query = makeQuery();
      switch (shouldCache) {
        case EVERYTHING:
          cachedQuery = query;
          break;
        case THREAD_LOCAL:
          threadQuery.set(query);
          break;
        case NOTHING:
        default:
          break;
      }
      return query;
    }
  }

  public void clear() {
    cachedGraph = null;
    cachedQuery = null;
    threadGraph.remove();
    threadQuery.remove();
  }

  /**
   * @author Karthick Sankarachary
   */
  public enum ShouldCache {
    /**
     * Cache the instances returned by {@link #graph()}, and {@link #query()}.
     *
     * <p> This mode may be used in single-threaded applications, if you want to avoid the overhead
     * of creating the state of the {@link #graph()} and {@link #query()} instances. The caller must
     * remember to finally invoke {@link Graph#reset()} after every use.
     */
    EVERYTHING,
    /**
     * Cache the instances returned by {@link #graph()}, and {@link #query()}.
     *
     * <p> This mode should be used in multi-threaded applications, where the resources underlying
     * the {@link #g} can handle concurrent requests. Since the {@link #graph()} } and {@link
     * #query()} instances need to be thread-safe, their states will be stored in a thread-local.
     * The caller thread must remember to finally invoke {@link Graph#reset()} after every use.
     */
    THREAD_LOCAL,
    /**
     * Do not cache instances returned by {@link #graph()}, or {@link #query()}.
     *
     * <p> This mode may be used in multi-threaded applications, when the resources underlying the
     * {@link #g} cannot handle concurrent requests, or it is preferable to create new "system"
     * resources per caller, or if the caller needs to employ custom caching schemes. Since
     * the {@link #graph()}  and {@link #query()} instances are not cached, there's no need to
     * {@link Graph#reset} them after every use, unless of source, the caller keeps a copy of it.
     */
    NOTHING
  }
}
