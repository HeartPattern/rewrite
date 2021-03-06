/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import lombok.Getter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectInserter;
import org.openrewrite.internal.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Change {
    /**
     * Possible {@code null} if a new file is being created.
     */
    @Getter
    @Nullable
    private final SourceFile original;

    /**
     * Possibly {@code null} if the change results in the file being deleted.
     */
    @Getter
    @Nullable
    private final SourceFile fixed;

    @Getter
    private final Set<String> visitorsThatMadeChanges;

    public Change(@Nullable SourceFile original, @Nullable SourceFile fixed, Set<String> visitorsThatMadeChanges) {
        this.original = original;
        this.fixed = fixed;
        this.visitorsThatMadeChanges = visitorsThatMadeChanges;
    }

    /**
     * @return Git-style patch diff representing the changes to this compilation unit
     */
    public String diff() {
        return diff(null);
    }

    /**
     * @param relativeTo Optional relative path that is used to relativize file paths of reported differences.
     * @return Git-style patch diff representing the changes to this compilation unit
     */
    public String diff(@Nullable Path relativeTo) {
        // FIXME fix source path when deleting files
        Path sourcePath = fixed instanceof SourceFile ?
                Paths.get(((SourceFile) fixed).getSourcePath()) :
                (relativeTo == null ? Paths.get(".") : relativeTo).resolve("partial-" + fixed.getId());

        return new InMemoryDiffEntry(sourcePath, relativeTo,
                original == null ? "" : original.print(), fixed.print(), visitorsThatMadeChanges).getDiff();
    }

    public Class<? extends Tree> getTreeType() {
        return original == null ?
                (fixed == null ? null : fixed.getClass()) :
                original.getClass();
    }

    static class InMemoryDiffEntry extends DiffEntry {
        InMemoryRepository repo;
        Set<String> rulesThatMadeChanges;

        InMemoryDiffEntry(Path filePath, @Nullable Path relativeTo, String oldSource, String newSource, Set<String> rulesThatMadeChanges) {
            this.changeType = ChangeType.MODIFY;
            this.rulesThatMadeChanges = rulesThatMadeChanges;

            Path relativePath = relativeTo == null ? filePath : relativeTo.relativize(filePath);
            this.oldPath = relativePath.toString();
            this.newPath = relativePath.toString();

            try {
                this.repo = new InMemoryRepository.Builder()
                        .setRepositoryDescription(new DfsRepositoryDescription())
                        .build();

                ObjectInserter inserter = repo.getObjectDatabase().newInserter();
                oldId = inserter.insert(Constants.OBJ_BLOB, oldSource.getBytes()).abbreviate(40);
                newId = inserter.insert(Constants.OBJ_BLOB, newSource.getBytes()).abbreviate(40);
                inserter.flush();

                oldMode = FileMode.REGULAR_FILE;
                newMode = FileMode.REGULAR_FILE;
                repo.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String getDiff() {
            if (oldId.equals(newId)) {
                return "";
            }

            ByteArrayOutputStream patch = new ByteArrayOutputStream();
            DiffFormatter formatter = new DiffFormatter(patch);
            formatter.setRepository(repo);
            try {
                formatter.format(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            String diff = new String(patch.toByteArray());

            AtomicBoolean addedComment = new AtomicBoolean(false);
            // NOTE: String.lines() would remove empty lines which we don't want
            return Arrays.stream(diff.split("\n"))
                    .map(l -> {
                        if (!addedComment.get() && l.startsWith("@@") && l.endsWith("@@")) {
                            addedComment.set(true);
                            return l + rulesThatMadeChanges.stream()
                                    .sorted()
                                    .collect(Collectors.joining(", ", " ", ""));
                        }
                        return l;
                    })
                    .collect(Collectors.joining("\n")) + "\n";
        }
    }
}
