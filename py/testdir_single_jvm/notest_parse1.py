import unittest
import re, os, shutil, sys
sys.path.extend(['.','..','py'])
import h2o, h2o_cmd

# test some random csv data, and some lineend combinations
class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_parse1(self):
        csvPathname = h2o.find_file('smalldata/parse1.csv')
        h2o_cmd.runRF(trees=37, timeoutSecs=10, csvPathname=csvPathname)


if __name__ == '__main__':
    h2o.unit_main()
