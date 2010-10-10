package org.nachodb.impl;
import  org.nachodb.*;

import java.util.*;

class ThickIndex extends PersistentResource implements Index { 
    private Index index;
    private int   nElems;

    static final int BTREE_THRESHOLD = 128;

    ThickIndex(Class keyType, StorageImpl db) { 
        super(db);
        index = db.createIndex(keyType, true);
    }
    
    ThickIndex() {}

    public IPersistent get(Key key) {
        IPersistent s = index.get(key);
        if (s == null) { 
            return null;
        }
        if (s instanceof Relation) { 
            Relation r = (Relation)s;
            if (r.size() == 1) { 
                return r.get(0);
            }
        }
        throw new StorageError(StorageError.KEY_NOT_UNIQUE);
    }
                  
    public IPersistent[] get(Key from, Key till) {
        return extend(index.get(from, till));
    }
     
    private IPersistent[] extend( IPersistent[] s) { 
        ArrayList list = new ArrayList();
        for (int i = 0; i < s.length; i++) { 
            IPersistent p = s[i];
            Iterator iterator = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
            while (iterator.hasNext()) { 
                list.add(iterator.next());
            }
        }
        return (IPersistent[])list.toArray(new IPersistent[list.size()]);
    }

    public IPersistent get(String key) {
        return get(new Key(key));
    }
                      
    public IPersistent[] getPrefix(String prefix) { 
        return extend(index.getPrefix(prefix));
    }
    
    public IPersistent[] prefixSearch(String word) { 
        return extend(index.prefixSearch(word));
    }
           
    public int size() { 
        return nElems;
    }
    
    public void clear() { 
        Iterator iterator = index.iterator();
        while (iterator.hasNext()) { 
            ((IPersistent)iterator.next()).deallocate();
        }
        index.clear();
        nElems = 0;
        modify();
    }

    public IPersistent[] toPersistentArray() { 
        return extend(index.toPersistentArray());
    }
        
    public IPersistent[] toPersistentArray(IPersistent[] arr) { 
        IPersistent[] s = index.toPersistentArray();
        ArrayList list = new ArrayList();
        for (int i = 0; i < s.length; i++) { 
            IPersistent p = s[i];
            Iterator iterator = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
            while (iterator.hasNext()) { 
                list.add(iterator.next());
            }
        }
        return (IPersistent[])list.toArray(arr);
    }

    static class ExtendIterator implements Iterator {  
        public boolean hasNext() { 
            return inner != null;
        }

        public Object next() { 
            Object obj = inner.next();
            if (!inner.hasNext()) {                 
                if (outer.hasNext()) {
                    Object p = outer.next();
                    inner = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
                } else { 
                    inner = null;
                }
            }
            return obj;
        }

        public void remove() { 
            throw new UnsupportedOperationException();
        }

        ExtendIterator(Iterator iterator) { 
            outer = iterator;
            if (iterator.hasNext()) { 
                Object p = iterator.next();
                inner = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
            }
        }

        private Iterator outer;
        private Iterator inner;
    }

    static class ExtendEntry implements Map.Entry {
        public Object getKey() { 
            return key;
        }

        public Object getValue() { 
            return value;
        }

        public Object setValue(Object value) { 
            throw new UnsupportedOperationException();
        }

        ExtendEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        private Object key;
        private Object value;
    }

    static class ExtendEntryIterator implements Iterator {  
        public boolean hasNext() { 
            return inner != null;
        }

        public Object next() { 
            Map.Entry curr = new ExtendEntry(key, inner.next());
            if (!inner.hasNext()) {                 
                if (outer.hasNext()) {
                    Map.Entry entry = (Map.Entry)outer.next();
                    key = entry.getKey();
                    Object p = entry.getValue();
                    inner = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
                } else { 
                    inner = null;
                }
            }
            return curr;
        }

        public void remove() { 
            throw new UnsupportedOperationException();
        }

        ExtendEntryIterator(Iterator iterator) { 
            outer = iterator;
            if (iterator.hasNext()) { 
                Map.Entry entry = (Map.Entry)iterator.next();
                key = entry.getKey();
                Object p = entry.getValue();
                inner = (p instanceof Relation) ? ((Relation)p).iterator() : ((IPersistentSet)p).iterator();
            }
        }

        private Iterator outer;
        private Iterator inner;
        private Object   key;
    }


    public Iterator iterator() { 
        return new ExtendIterator(index.iterator());
    }
    
    public Iterator entryIterator() { 
        return new ExtendEntryIterator(index.entryIterator());
    }

    public Iterator iterator(Key from, Key till, int order) { 
        return new ExtendIterator(index.iterator(from, till, order));
    }
        
    public Iterator entryIterator(Key from, Key till, int order) { 
        return new ExtendEntryIterator(index.entryIterator(from, till, order));
    }

    public Iterator prefixIterator(String prefix) { 
        return new ExtendIterator(index.prefixIterator(prefix));
    }

    public Class getKeyType() { 
        return index.getKeyType();
    }

    public boolean put(Key key, IPersistent obj) { 
        IPersistent s = index.get(key);
        if (s == null) { 
            Relation r = getStorage().createRelation(null);
            r.add(obj);
            index.put(key, r);
        } else if (s instanceof Relation) { 
            Relation r = (Relation)s;
            if (r.size() == BTREE_THRESHOLD) {
                IPersistentSet ps = getStorage().createSet();
                for (int i = 0; i < BTREE_THRESHOLD; i++) { 
                    ps.add(r.getRaw(i));
                }
                ps.add(obj);
                index.set(key, ps);
                r.deallocate();
            } else { 
                r.add(obj);
            }
        } else { 
            ((IPersistentSet)s).add(obj);
        }
        nElems += 1;
        modify();
        return true;
    }

    public IPersistent set(Key key, IPersistent obj) {
        IPersistent s = index.get(key);
        if (s == null) { 
            Relation r = getStorage().createRelation(null);
            r.add(obj);
            index.put(key, r);
            nElems += 1;
            modify();
            return null;
        } else if (s instanceof Relation) { 
            Relation r = (Relation)s;
            if (r.size() == 1) {
                IPersistent prev = r.get(0);
                r.set(0, obj);
                return prev;
            } 
        }
        throw new StorageError(StorageError.KEY_NOT_UNIQUE);
    }

    public void remove(Key key, IPersistent obj) { 
        IPersistent s = index.get(key);
        if (s instanceof Relation) { 
            Relation r = (Relation)s;
            int i = r.indexOf(obj);
            if (i >= 0) { 
                r.remove(i);
                if (r.size() == 0) { 
                    index.remove(key, r);
                    r.deallocate();
                }
                nElems -= 1;
                modify();
                return;
            }
        } else if (s instanceof IPersistentSet) { 
            IPersistentSet ps = (IPersistentSet)s;
            if (ps.remove(obj)) { 
                if (ps.size() == 0) { 
                    index.remove(key, ps);
                    ps.deallocate();
                }                    
                nElems -= 1;
                modify();
                return;
            }
        }
        throw new StorageError(StorageError.KEY_NOT_FOUND);
    }

    public IPersistent remove(Key key) {
        throw new StorageError(StorageError.KEY_NOT_UNIQUE);
    }

    public boolean put(String key, IPersistent obj) {
        return put(new Key(key), obj);
    }

    public IPersistent set(String key, IPersistent obj) {
        return set(new Key(key), obj);
    }

    public void remove(String key, IPersistent obj) {
        remove(new Key(key), obj);
    }

    public IPersistent remove(String key) {
        throw new StorageError(StorageError.KEY_NOT_UNIQUE);
    }

    public void deallocate() {
        clear();
        index.deallocate();
        super.deallocate();
    }
}