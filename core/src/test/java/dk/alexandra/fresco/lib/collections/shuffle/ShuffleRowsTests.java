/*
 * Copyright (c) 2015, 2016, 2017 FRESCO (http://github.com/aicis/fresco).
 *
 * This file is part of the FRESCO project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * FRESCO uses SCAPI - http://crypto.biu.ac.il/SCAPI, Crypto++, Miracl, NTL, and Bouncy Castle.
 * Please see these projects for any further licensing issues.
 *******************************************************************************/
package dk.alexandra.fresco.lib.collections.shuffle;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.Computation;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThread;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadConfiguration;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadFactory;
import dk.alexandra.fresco.framework.builder.ProtocolBuilderNumeric.SequentialNumericBuilder;
import dk.alexandra.fresco.framework.network.ResourcePoolCreator;
import dk.alexandra.fresco.framework.sce.resources.ResourcePool;
import dk.alexandra.fresco.lib.collections.Matrix;
import dk.alexandra.fresco.lib.collections.MatrixTestUtils;
import dk.alexandra.fresco.lib.collections.MatrixUtils;
import dk.alexandra.fresco.lib.collections.io.CloseMatrix;
import dk.alexandra.fresco.lib.collections.io.OpenMatrix;

public class ShuffleRowsTests {

  /**
   * Performs a ShuffleRows computation on a matrix of SInts.
   * 
   * @author nv
   *
   * @param <ResourcePoolT>
   */
  public static class TestShuffleRowsGeneric<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, SequentialNumericBuilder> {

    final Matrix<BigInteger> input;
    final Matrix<BigInteger> expected;

    TestShuffleRowsGeneric(Matrix<BigInteger> input, Matrix<BigInteger> expected) {
      this.input = input;
      this.expected = expected;
    }

    @Override
    public TestThread<ResourcePoolT, SequentialNumericBuilder> next(
        TestThreadConfiguration<ResourcePoolT, SequentialNumericBuilder> conf) {
      return new TestThread<ResourcePoolT, SequentialNumericBuilder>() {

        @Override
        public void test() throws Exception {
          // define functionality to be tested
          Application<Matrix<BigInteger>, SequentialNumericBuilder> testApplication = root -> {
            final int pid = conf.getMyId();
            // sort of hacky
            final int pids[] = new int[conf.getNoOfParties()];
            for (int i = 0; i < pids.length; i++) {
              pids[i] = i + 1;
            }
            return root.par(par -> {
              // close inputs
              return new CloseMatrix(input, 1).build(par);
            }).seq((closed, seq) -> {
              // shuffle
              return new ShuffleRows(closed, new Random(42 + pid), pid, pids).build(seq);
            }).par((swapped, par) -> {
              // open result
              Computation<Matrix<Computation<BigInteger>>> opened =
                  new OpenMatrix(swapped).build(par);
              return () -> new MatrixUtils().unwrapMatrix(opened.out());
            });
          };
          Matrix<BigInteger> actual = secureComputationEngine.runApplication(testApplication,
              ResourcePoolCreator.createResourcePool(conf.sceConf));
          assertThat(actual.getRows(), is(expected.getRows()));
        }
      };
    }
  }

  private static Matrix<BigInteger> clearTextShuffle(int[] pids, int seed, Matrix<BigInteger> input) {
    Random[] rands = new Random[pids.length];
    for (int i = 0; i < pids.length; i++) {
      rands[i] = new Random(seed + pids[i]);
    }
    ArrayList<ArrayList<BigInteger>> rows = input.getRows();
    for (int pid: pids) {
      Collections.shuffle(rows, rands[pid - 1]);
    }
    return new Matrix<>(input.getHeight(), input.getWidth(), rows);
  }

  // result depends on number of parties
  public static <ResourcePoolT extends ResourcePool> TestShuffleRowsGeneric<ResourcePoolT> shuffleRowsTwoParties() {
    // define input matrix
    MatrixTestUtils utils = new MatrixTestUtils();
    Matrix<BigInteger> input = utils.getInputMatrix(8, 3);
    Matrix<BigInteger> expected = clearTextShuffle(new int[]{1, 2}, 42, utils.getInputMatrix(8, 3));
    return new TestShuffleRowsGeneric<>(input, expected);
  }

  // result depends on number of parties
  public static <ResourcePoolT extends ResourcePool> TestShuffleRowsGeneric<ResourcePoolT> shuffleRowsThreeParties() {
    // define input matrix
    MatrixTestUtils utils = new MatrixTestUtils();
    Matrix<BigInteger> input = utils.getInputMatrix(8, 3);
    Matrix<BigInteger> expected = clearTextShuffle(new int[]{1, 2, 3}, 42, utils.getInputMatrix(8, 3));
    return new TestShuffleRowsGeneric<>(input, expected);
  }

  public static <ResourcePoolT extends ResourcePool> TestShuffleRowsGeneric<ResourcePoolT> shuffleRowsEmpty() {
    // define input matrix
    MatrixTestUtils utils = new MatrixTestUtils();
    Matrix<BigInteger> input = utils.getInputMatrix(0, 0);
    Matrix<BigInteger> expected = utils.getInputMatrix(0, 0);
    return new TestShuffleRowsGeneric<>(input, expected);
  }
}
