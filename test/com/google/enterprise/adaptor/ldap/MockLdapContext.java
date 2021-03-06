// Copyright 2014 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// copied directly from plexi.ldap/test/com/google/enterprise/adaptor/ad
// only change made was to package

package com.google.enterprise.adaptor.ldap;

import com.google.common.annotations.VisibleForTesting;

import java.io.*;
import java.util.*;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;

/**
 * Mock of {@link LdapContext}.
 */
public class MockLdapContext extends InitialLdapContext {
  private BasicAttributes attributes = new BasicAttributes();
  private Hashtable<String, Object> searchResults
      = new Hashtable<String, Object>();
  @VisibleForTesting
  Control[] controls = new Control[0];

  public MockLdapContext() throws NamingException {
  }

  @Override
  public Attributes getAttributes(String name) throws NamingException {
    return attributes;
  }

  /** Normal way to add attributes to this Mock */
  public MockLdapContext addAttribute(String key, Object value) {
    attributes.put(key, value);
    return this;
  }

  public MockLdapContext removeAttribute(String key) {
    attributes.remove(key);
    return this;
  }

  public static String makeKey(String filter, String attribute, String baseDn) {
    return filter + "|" + attribute + "|" + baseDn;
  }

  public MockLdapContext addSearchResult(String baseDn,
      String filter, String attribute, Object value) {
    searchResults.put(makeKey(filter, attribute, baseDn), value);
    return this;
  }

  /** Returns a <code>NamingEnumeration</code> of <code>SearchResult</code>s */
  public NamingEnumeration<SearchResult> search(String base, String filter,
      SearchControls searchControls) throws NamingException {
    Vector<SearchResult> results = new Vector<SearchResult>();
    SearchResult currentResult = null;
    for (String attribute : searchControls.getReturningAttributes()) {
      Object o = searchResults.get(makeKey(filter, attribute, base));
      if (o == null) {
        // hack to make search for member;Range=X-Y succeed when that's the
        // final range present
        if (attribute.startsWith("member;Range=")) {
          attribute += "*";
          o = searchResults.get(makeKey(filter, attribute, base));
        }
      }
      if (o != null) {
        if (currentResult == null) {
          currentResult = makeSearchResult(attribute, o, base);
          results.add(currentResult);
        } else {
          Attributes attrs = currentResult.getAttributes();
          this.addAttribute(attrs, attribute, o);
          currentResult.setAttributes(attrs);
        }
      }
    }
    return new SearchResultsNamingEnumeration(results);
  }

  /** properly handle collections as we add a new attribute */
  private static void addAttribute(Attributes attrs, String newAttr,
      Object value) {
    attrs.put(newAttr, value);
    if (value instanceof Collection<?>) {
      Attribute attr = attrs.get(newAttr);
      attr.clear();
      for (Object member: (Collection<?>) value) {
        attr.add(member);
      }
    }
  }

  /** Creates a <code>SearchResults</code> wrapper with a single attribute */
  private static SearchResult makeSearchResult(String attribute, Object o,
      String baseDn) {
    SearchResult sr = new SearchResult("search result name", o,
        new BasicAttributes());
    addAttribute(sr.getAttributes(), attribute, o);
    sr.setNameInNamespace("cn=name\\ under," + baseDn);
    return sr;
  }

  @Override
  public Control[] getResponseControls() throws NamingException {
    return controls;
  };

  /**
   * Stores the controls (which are returned in getResponseControls() above.)
   * This class does nothing else with the controls (they are not interpreted
   * in any way, unlike what a "real" LdapContext would do).
   */
  @Override
  public void setRequestControls(Control[] requestControls)
      throws NamingException {
    controls = requestControls;
  };

  @VisibleForTesting
  static class SearchResultsNamingEnumeration
      implements NamingEnumeration<SearchResult> {
    private final Enumeration<SearchResult> results;
    private SearchResult nextElement = null;

    SearchResultsNamingEnumeration(Vector<SearchResult> results) {
      this.results = results.elements();
    }

    private SearchResult getNextElement() {
      if (results.hasMoreElements()) {
        return results.nextElement();
      }
      return null;
    }

    @Override
    public boolean hasMore() {
      if (nextElement != null) {
        return true;
      }
      nextElement = getNextElement();
      return (nextElement != null);
    }

    @Override
    public boolean hasMoreElements() {
      return hasMore();
    }

    @Override
    public SearchResult next() {
      if (!hasMore()) {
        throw new NoSuchElementException();
      }
      SearchResult res = nextElement;
      nextElement = null;
      return res;
    }

    @Override
    public SearchResult nextElement() {
      return next();
    }

    @Override
    public void close() {
    }
  }
}
