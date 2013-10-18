/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs.internal;

import static com.google.jimfs.testing.PathSubject.paths;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.path.Normalization;
import com.google.jimfs.path.PathType;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/**
 * Tests for {@link PathService}.
 *
 * @author Colin Decker
 */
public class PathServiceTest {
  
  private static final ImmutableSet<Normalization> NO_NORMALIZATIONS = ImmutableSet.of();

  private final PathService service = fakeUnixPathService();

  @Test
  public void testBasicProperties() {
    ASSERT.that(service.getSeparator()).is("/");
    ASSERT.that(fakeWindowsPathService().getSeparator()).is("\\");
  }

  @Test
  public void testPathCreation() {
    ASSERT.about(paths()).that(service.emptyPath())
        .hasRootComponent(null).and()
        .hasNameComponents("");

    ASSERT.about(paths()).that(service.createRoot(service.name("/")))
        .isAbsolute().and()
        .hasRootComponent("/").and()
        .hasNoNameComponents();

    ASSERT.about(paths()).that(service.createFileName(service.name("foo")))
        .hasRootComponent(null).and()
        .hasNameComponents("foo");

    JimfsPath relative = service.createRelativePath(service.names(ImmutableList.of("foo", "bar")));
    ASSERT.about(paths()).that(relative)
        .hasRootComponent(null).and()
        .hasNameComponents("foo", "bar");

    JimfsPath absolute = service.createPath(
        service.name("/"), service.names(ImmutableList.of("foo", "bar")));
    ASSERT.about(paths()).that(absolute)
        .isAbsolute().and()
        .hasRootComponent("/").and()
        .hasNameComponents("foo", "bar");
  }

  @Test
  public void testPathCreation_emptyPath() {
    // normalized to empty path with single empty string name
    ASSERT.about(paths()).that(service.createPath(null, ImmutableList.<Name>of()))
        .hasRootComponent(null).and()
        .hasNameComponents("");
  }

  @Test
  public void testPathCreation_parseIgnoresEmptyString() {
    // if the empty string wasn't ignored, the resulting path would be "/foo" since the empty
    // string would be joined with foo
    ASSERT.about(paths()).that(service.parsePath("", "foo"))
        .hasRootComponent(null).and()
        .hasNameComponents("foo");
  }

  @Test
  public void testToString() {
    // not much to test for this since it just delegates to PathType anyway
    JimfsPath path = new JimfsPath(
        service, null, ImmutableList.of(Name.simple("foo"), Name.simple("bar")));
    ASSERT.that(service.toString(path)).is("foo/bar");

    path = new JimfsPath(service, Name.simple("/"), ImmutableList.of(Name.simple("foo")));
    ASSERT.that(service.toString(path)).is("/foo");
  }

  @Test
  public void testHash_usingDisplayForm() {
    PathService pathService = fakePathService(PathType.unix(), false);

    JimfsPath path1 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("FOO", "foo")));
    JimfsPath path2 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("FOO", "FOO")));
    JimfsPath path3 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("FOO", "9874238974897189741")));

    ASSERT.that(pathService.hash(path1)).isEqualTo(pathService.hash(path2));
    ASSERT.that(pathService.hash(path2)).isEqualTo(pathService.hash(path3));
  }

  @Test
  public void testHash_usingCanonicalForm() {
    PathService pathService = fakePathService(PathType.unix(), true);

    JimfsPath path1 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("foo", "foo")));
    JimfsPath path2 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("FOO", "foo")));
    JimfsPath path3 = new JimfsPath(pathService, null,
        ImmutableList.of(Name.create("28937497189478912374897", "foo")));

    ASSERT.that(pathService.hash(path1)).isEqualTo(pathService.hash(path2));
    ASSERT.that(pathService.hash(path2)).isEqualTo(pathService.hash(path3));
  }

  @Test
  public void testCompareTo_usingDisplayForm() {
    PathService pathService = fakePathService(PathType.unix(), false);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("a", "z")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("b", "y")));
    JimfsPath path3 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("c", "x")));

    ASSERT.that(pathService.compare(path1, path2)).is(-1);
    ASSERT.that(pathService.compare(path2, path3)).is(-1);
  }

  @Test
  public void testCompareTo_usingCanonicalForm() {
    PathService pathService = fakePathService(PathType.unix(), true);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("a", "z")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("b", "y")));
    JimfsPath path3 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("c", "x")));

    ASSERT.that(pathService.compare(path1, path2)).is(1);
    ASSERT.that(pathService.compare(path2, path3)).is(1);
  }

  @Test
  public void testPathMatcher() {
    ASSERT.that(service.createPathMatcher("regex:foo")).isA(PathMatchers.RegexPathMatcher.class);
    ASSERT.that(service.createPathMatcher("glob:foo")).isA(PathMatchers.RegexPathMatcher.class);
  }

  public static PathService fakeUnixPathService() {
    return fakePathService(PathType.unix(), false);
  }

  public static PathService fakeWindowsPathService() {
    return fakePathService(PathType.windows(), false);
  }

  public static PathService fakePathService(PathType type, boolean equalityUsesCanonicalForm) {
    PathService service = new PathService(
        type, NO_NORMALIZATIONS, NO_NORMALIZATIONS, equalityUsesCanonicalForm);
    service.setFileSystem(FAKE_FILE_SYSTEM);
    return service;
  }

  private static final FileSystem FAKE_FILE_SYSTEM = new FileSystem() {
    @Override
    public FileSystemProvider provider() {
      return null;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public String getSeparator() {
      return null;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
      return null;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
      return null;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
      return null;
    }

    @Override
    public Path getPath(String first, String... more) {
      return null;
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
      return null;
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
      return null;
    }

    @Override
    public WatchService newWatchService() throws IOException {
      return null;
    }
  };
}
