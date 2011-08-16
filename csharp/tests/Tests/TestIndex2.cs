namespace Volante
{
    using System;
    using System.Collections;

    public class TestIndex2Result : TestResult
    {
        public TimeSpan InsertTime;
        public TimeSpan IndexSearch;
        public TimeSpan IterationTime;
        public TimeSpan RemoveTime;
        public ICollection MemoryUsage; // elements are of MemoryUsage type
    }

    public class TestIndex2
    {
        public class Record : Persistent
        {
            public string strKey;
            public long intKey;
        }

        public class Root : Persistent
        {
            public ISortedCollection<string, Record> strIndex;
            public ISortedCollection<long, Record> intIndex;
        }

        public class IntRecordComparator : PersistentComparator<long, Record>
        {
            public override int CompareMembers(Record m1, Record m2)
            {
                long diff = m1.intKey - m2.intKey;
                return diff < 0 ? -1 : diff == 0 ? 0 : 1;
            }

            public override int CompareMemberWithKey(Record mbr, long key)
            {
                long diff = mbr.intKey - key;
                return diff < 0 ? -1 : diff == 0 ? 0 : 1;
            }
        }

        public class StrRecordComparator : PersistentComparator<string, Record>
        {
            public override int CompareMembers(Record m1, Record m2)
            {
                return m1.strKey.CompareTo(m2.strKey);
            }

            public override int CompareMemberWithKey(Record mbr, string key)
            {
                return mbr.strKey.CompareTo(key);
            }
        }

        public void Run(TestConfig config)
        {
            int i;
            int count = config.Count;
            var res = new TestIndex2Result();
            config.Result = res;
            var start = DateTime.Now;

            IStorage db = config.GetDatabase();
            Root root = (Root)db.Root;
            Tests.Assert(root == null);
            root = new Root();
            root.strIndex = db.CreateSortedCollection<string, Record>(new StrRecordComparator(), true);
            root.intIndex = db.CreateSortedCollection<long, Record>(new IntRecordComparator(), true);
            db.Root = root;

            ISortedCollection<long, Record> intIndex = root.intIndex;
            ISortedCollection<string, Record> strIndex = root.strIndex;
            long key = 1999;
            for (i = 0; i < count; i++)
            {
                Record rec = new Record();
                rec.intKey = key;
                rec.strKey = System.Convert.ToString(key);
                intIndex.Add(rec);
                strIndex.Add(rec);
                key = (3141592621L * key + 2718281829L) % 1000000007L;
            }
            db.Commit();
            res.InsertTime = DateTime.Now - start;

            start = System.DateTime.Now;
            key = 1999;
            for (i = 0; i < count; i++)
            {
                Record rec1 = intIndex[key];
                Record rec2 = strIndex[Convert.ToString(key)];
                Tests.Assert(rec1 != null && rec1 == rec2);
                key = (3141592621L * key + 2718281829L) % 1000000007L;
            }
            res.IndexSearch = DateTime.Now - start;

            start = System.DateTime.Now;
            key = Int64.MinValue;
            i = 0;
            foreach (Record rec in intIndex)
            {
                Tests.Assert(rec.intKey >= key);
                key = rec.intKey;
                i += 1;
            }
            Tests.Assert(i == count);
            i = 0;
            String strKey = "";
            foreach (Record rec in strIndex)
            {
                Tests.Assert(rec.strKey.CompareTo(strKey) >= 0);
                strKey = rec.strKey;
                i += 1;
            }
            Tests.Assert(i == count);
            res.IterationTime = DateTime.Now - start;

            start = DateTime.Now;
            res.MemoryUsage = db.GetMemoryDump().Values;
#if NOT_ENABLED
            Console.WriteLine("Memory usage");
            foreach (MemoryUsage usage in db.GetMemoryDump().Values)
            {
                Console.WriteLine(" " + usage.type.Name + ": instances=" + usage.nInstances + ", total size=" + usage.totalSize + ", allocated size=" + usage.allocatedSize);
            }
            Console.WriteLine("Elapsed time for memory dump: " + (DateTime.Now - start));
#endif

            start = System.DateTime.Now;
            key = 1999;
            for (i = 0; i < count; i++)
            {
                Record rec = intIndex[key];
                intIndex.Remove(rec);
                strIndex.Remove(rec);
                rec.Deallocate();
                key = (3141592621L * key + 2718281829L) % 1000000007L;
            }
            res.RemoveTime = DateTime.Now - start;
            db.Close();
        }
    }

}
