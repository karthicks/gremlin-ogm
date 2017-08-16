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
import org.apache.tinkerpop.gremlin.object.traversal.Query;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import lombok.SneakyThrows;

import static org.apache.tinkerpop.gremlin.object.provider.GraphFactory.ShouldCache.EVERYTHING;
import static org.apache.tinkerpop.gremlin.object.provider.GraphFactory.ShouldCache.NOTHING;
import static org.apache.tinkerpop.gremlin.object.provider.GraphFactory.ShouldCache.THREAD_LOCAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

/**
 * The {@link GraphFactoryTest} defines sanity tests to ensure that the {@link Graph} and {@link
 * Query} instances provided by the {@link #factory()} method work.
 *
 * @author Karthick Sankarachary (http://github.com/karthicks)
 */
@RunWith(Parameterized.class)
@SuppressWarnings("rawtypes")
public class GraphFactoryTest {

  @Parameterized.Parameter
  public GraphFactory.ShouldCache shouldCache;
  private Graph graph;
  private Query query;
  private GraphTraversalSource g;
  private GraphFactory factory;
  private ExecutorService executorService;

  @Parameterized.Parameters(name = "ShouldCache({0})")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {EVERYTHING}, {THREAD_LOCAL}, {NOTHING}
    });
  }

  protected GraphFactory factory() {
    if (factory == null) {
      g = mock(GraphTraversalSource.class);
      factory = GraphFactory.of(g, shouldCache);
      factory.clear();
    }
    return factory;
  }

  @Before
  public void setUp() {
    executorService = new ForkJoinPool();
    graph = factory().graph();
    query = factory().query();
  }

  @After
  public void tearDown() {
    factory = null;
    graph.close();
    query.close();
  }

  @Test
  @SneakyThrows
  public void testCacheBehavior() {
    switch (shouldCache) {
      case EVERYTHING:
        assertEquals(factory().graph(), graph);
        assertEquals(factory().query(), query);
        break;
      case THREAD_LOCAL:
        executorService.submit(() -> {
          assertNotEquals(factory().graph(), graph);
          assertNotEquals(factory().query(), query);
        }).get();
        assertEquals(factory().graph(), graph);
        assertEquals(factory().query(), query);
        break;
      case NOTHING:
      default:
        assertNotEquals(factory().graph(), graph);
        assertNotEquals(factory().query(), query);
        break;
    }
  }
}
