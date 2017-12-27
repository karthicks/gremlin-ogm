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
package org.apache.tinkerpop.gremlin.object.traversal.library;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.apache.tinkerpop.gremlin.object.traversal.ElementTo;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Element;

/**
 * {@link Select} applies the ${link GraphTraversal#count} step on the current traversal.
 *
 * @author Karthick Sankarachary
 */
@Data
public class Select implements ElementTo.Map {

  private String selectKey1;
  private String selectKey2;
  private List<String> otherSelectKeys;

  @SuppressWarnings("PMD.ShortMethodName")
  public static Select of(String... aliases) {
    Select select = new Select();
    if (aliases.length > 0) {
      select.selectKey1 = aliases[0];
    }
    if (aliases.length > 1) {
      select.selectKey2 = aliases[1];
    }
    if (aliases.length > 2) {
      select.otherSelectKeys = Arrays.asList(aliases);
      select.otherSelectKeys.remove(0);
      select.otherSelectKeys.remove(0);
    } else {
      select.otherSelectKeys = new ArrayList<>();
    }
    return select;
  }


  @Override
  public GraphTraversal<Element, Map<String, Object>> apply(
      GraphTraversal<Element, Element> traversal) {
    GraphTraversal<Element, Map<String, Object>> selectTraversal;
    if (selectKey2 == null) {
      selectTraversal = traversal.select(selectKey1);
    } else {
      selectTraversal =
          traversal.select(selectKey1, selectKey2, otherSelectKeys.toArray(new String[] {}));
    }
    if (selectKey1 != null) {
      selectTraversal.by();
    }
    if (selectKey2 != null) {
      selectTraversal.by();
    }
    otherSelectKeys.forEach(otherSelectKey -> {
      selectTraversal.by();
    });
    return selectTraversal;
  }
}
