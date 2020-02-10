/*
 * Copyright 2020 the original authors.
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
package com.netflix.rewrite.tree.visitor.refactor;

import com.netflix.rewrite.internal.StringUtils;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.CursorAstVisitor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;

@RequiredArgsConstructor
public class Formatter {
    Tr.CompilationUnit cu;

    @NonFinal
    Result wholeSourceIndent;

    @RequiredArgsConstructor
    @Getter
    public static class Result {
        int enclosingIndent;
        int indentToUse;
        boolean indentedWithSpaces;
    }

    public Result findIndent(int enclosingIndent, Iterable<? extends Tree> trees) {
        if(wholeSourceIndent == null) {
            var wholeSourceIndentVisitor = new FindIndentVisitor(0);
            wholeSourceIndentVisitor.visit(cu);
            wholeSourceIndent = new Result(0, wholeSourceIndentVisitor.getMostCommonIndent() > 0 ?
                    wholeSourceIndentVisitor.getMostCommonIndent() : 4 /* default to 4 spaces */,
                    wholeSourceIndentVisitor.isIndentedWithSpaces());
        }

        var findIndentVisitor = new FindIndentVisitor(enclosingIndent);
        trees.forEach(findIndentVisitor::visit);

        var indentToUse = findIndentVisitor.getMostCommonIndent() > 0 ?
                findIndentVisitor.getMostCommonIndent() :
                wholeSourceIndent.getIndentToUse();
        var indentedWithSpaces = findIndentVisitor.getTotalLines() > 0 ? findIndentVisitor.isIndentedWithSpaces() :
                wholeSourceIndent.isIndentedWithSpaces();

        return new Result(enclosingIndent, indentToUse, indentedWithSpaces);
    }

    public Formatting format(Tr.Block<?> relativeToEnclosing) {
        Result indentation = findIndent(relativeToEnclosing.getIndent(), relativeToEnclosing.getStatements());

        var indentedPrefix = range(0, indentation.getIndentToUse() + indentation.getEnclosingIndent())
                .mapToObj(i -> indentation.isIndentedWithSpaces() ? " " : "\t")
                .collect(joining("", "\n", ""));

        return Formatting.format(indentedPrefix);
    }

    /**
     * @param moving The tree that is moving
     * @param into The block the tree is moving into
     * @return A shift right format visitor that can be appended to a refactor visitor pipeline
     */
    public ShiftFormatRightVisitor shiftRight(Tree moving, Tree into, Tree enclosesBoth) {
        // NOTE: This isn't absolutely perfect... suppose the block moving was indented with tabs and the surrounding source was spaces.
        // Should be close enough in the vast majority of cases.
        int shift = enclosingIndent(into) - findIndent(enclosingIndent(enclosesBoth), singletonList(moving)).getEnclosingIndent();
        return new ShiftFormatRightVisitor(moving.getId(), shift, wholeSourceIndent.isIndentedWithSpaces());
    }

    private int enclosingIndent(Tree enclosesBoth) {
        return enclosesBoth instanceof Tr.Block ? ((Tr.Block<?>) enclosesBoth).getIndent() :
                (int) enclosesBoth.getFormatting().getPrefix().chars().dropWhile(c -> c == '\n' || c == '\r')
                        .takeWhile(Character::isWhitespace).count();
    }

    /**
     * Discover the most common indentation level of a tree, and whether this indentation is built with spaces or tabs.
     */
    @RequiredArgsConstructor
    private static class FindIndentVisitor extends CursorAstVisitor<Integer> {
        SortedMap<Integer, Long> indentFrequencies = new TreeMap<>();

        int enclosingIndent;

        @NonFinal
        int linesWithSpaceIndents = 0;

        @NonFinal
        int linesWithTabIndents = 0;

        @Override
        public Integer defaultTo(Tree t) {
            return null;
        }

        @Override
        public Integer visitTree(Tree tree) {
            String prefix = tree.getFormatting().getPrefix();
            if (prefix.chars().takeWhile(c -> c == '\n' || c == '\r').count() > 0) {
                indentFrequencies.merge((int) prefix.chars()
                                .dropWhile(c -> c == '\n' || c == '\r')
                                .takeWhile(Character::isWhitespace)
                                .count() - enclosingIndent,
                        1L, Long::sum);

                Map<Boolean, Long> indentTypeCounts = prefix.chars().dropWhile(c -> c == '\n' || c == '\r')
                        .takeWhile(Character::isWhitespace)
                        .mapToObj(c -> c == ' ')
                        .collect(Collectors.groupingBy(identity(), counting()));

                if (indentTypeCounts.getOrDefault(true, 0L) >= indentTypeCounts.getOrDefault(false, 0L)) {
                    linesWithSpaceIndents++;
                } else {
                    linesWithTabIndents++;
                }
            }

            return super.visitTree(tree);
        }

        public boolean isIndentedWithSpaces() {
            return linesWithSpaceIndents >= linesWithTabIndents;
        }

        @Override
        public Integer visitEnd() {
            return getMostCommonIndent();
        }

        public int getMostCommonIndent() {
            indentFrequencies.remove(0);
            return StringUtils.mostCommonIndent(indentFrequencies);
        }

        /**
         * @return The total number of source lines that this indent decision was made on.
         */
        public int getTotalLines() {
            return linesWithSpaceIndents + linesWithTabIndents;
        }
    }
}