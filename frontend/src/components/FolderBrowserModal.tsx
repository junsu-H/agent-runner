import { useCallback, useEffect, useRef, useState } from 'react';

type DirEntry = { name: string; path: string };
type DirListResponse = { current: string; parent: string | null; directories: DirEntry[] };

type Column = {
  path: string;
  dirs: DirEntry[];
  selected: string | null;
};

type Props = {
  browserOpen: boolean;
  setBrowserOpen: (open: boolean) => void;
  initialPath: string;
  onConfirm: (path: string) => void;
};

export function FolderBrowserModal({ browserOpen, setBrowserOpen, initialPath, onConfirm }: Props) {
  const [columns, setColumns] = useState<Column[]>([]);
  const [loading, setLoading] = useState(false);
  const [addressInput, setAddressInput] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  const fetchDirsApi = useCallback(async (path: string): Promise<DirListResponse | null> => {
    try {
      const res = await fetch(`/api/directories?path=${encodeURIComponent(path)}`);
      if (!res.ok) return null;
      return await res.json();
    } catch { return null; }
  }, []);

  /* ── Open: load initial path ── */
  useEffect(() => {
    if (!browserOpen) { setColumns([]); return; }
    let cancelled = false;
    (async () => {
      setLoading(true);
      const data = await fetchDirsApi(initialPath || '');
      if (!cancelled && data) {
        setColumns([{ path: data.current, dirs: data.directories, selected: null }]);
        setAddressInput(data.current);
      }
      if (!cancelled) setLoading(false);
    })();
    return () => { cancelled = true; };
  }, [browserOpen, initialPath, fetchDirsApi]);

  /* ── Auto-scroll right when columns added ── */
  useEffect(() => {
    requestAnimationFrame(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
      }
    });
  }, [columns.length]);

  /* ── Derive selected path ── */
  const getSelectedPath = () => {
    for (let i = columns.length - 1; i >= 0; i--) {
      if (columns[i].selected) return columns[i].selected!;
    }
    return columns.length > 0 ? columns[0].path : '';
  };

  /* ── Click a folder in column ── */
  const handleSelect = async (colIndex: number, dirPath: string) => {
    const trimmed = columns.slice(0, colIndex + 1);
    trimmed[colIndex] = { ...trimmed[colIndex], selected: dirPath };
    setColumns(trimmed);
    setAddressInput(dirPath);

    const data = await fetchDirsApi(dirPath);
    if (data) {
      setColumns(prev => {
        const current = prev.slice(0, colIndex + 1);
        current[colIndex] = { ...current[colIndex], selected: dirPath };
        return [...current, { path: data.current, dirs: data.directories, selected: null }];
      });
    }
  };

  /* ── Navigate to parent (prepend column) ── */
  const handleParent = async () => {
    if (columns.length === 0) return;
    const firstCol = columns[0];
    const firstData = await fetchDirsApi(firstCol.path);
    if (firstData?.parent) {
      const parentData = await fetchDirsApi(firstData.parent);
      if (parentData) {
        setColumns(prev => [
          { path: parentData.current, dirs: parentData.directories, selected: firstCol.path },
          ...prev,
        ]);
        requestAnimationFrame(() => {
          if (scrollRef.current) scrollRef.current.scrollLeft = 0;
        });
      }
    }
  };

  /* ── Address bar navigation ── */
  const handleAddressNavigate = async () => {
    setLoading(true);
    const data = await fetchDirsApi(addressInput);
    if (data) {
      setColumns([{ path: data.current, dirs: data.directories, selected: null }]);
      setAddressInput(data.current);
    }
    setLoading(false);
  };

  const handleAddressKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleAddressNavigate();
  };

  /* ── Confirm selection ── */
  const handleConfirm = () => {
    const selected = getSelectedPath();
    if (selected) onConfirm(selected);
    setBrowserOpen(false);
  };

  if (!browserOpen) return null;

  const selectedPath = getSelectedPath();

  return (
    <div className="fb-overlay" onClick={() => setBrowserOpen(false)}>
      <div className="fb-dialog fb-dialog-columns" onClick={(e) => e.stopPropagation()}>
        <div className="fb-titlebar">
          <span className="fb-title">Skill 폴더 선택</span>
          <button type="button" className="fb-close" onClick={() => setBrowserOpen(false)}>&times;</button>
        </div>

        <div className="fb-addressbar">
          <button type="button" className="fb-nav-btn" onClick={handleParent} title="상위 폴더">&uarr;</button>
          <input
            className="fb-address-input"
            value={addressInput}
            onChange={(e) => setAddressInput(e.target.value)}
            onKeyDown={handleAddressKeyDown}
            placeholder="경로 입력 후 Enter"
          />
          <button type="button" className="fb-nav-btn" onClick={() => void handleAddressNavigate()} title="이동">&rarr;</button>
        </div>

        <div className="fb-columns" ref={scrollRef}>
          {loading && columns.length === 0 ? (
            <div className="fb-empty">불러오는 중...</div>
          ) : columns.length === 0 ? (
            <div className="fb-empty">폴더를 불러올 수 없습니다.</div>
          ) : (
            columns.map((col, colIdx) => (
              <div key={`${col.path}-${colIdx}`} className="fb-column">
                <div className="fb-column-header">{col.path.split(/[\\/]/).pop() || col.path}</div>
                {col.dirs.length === 0 ? (
                  <div className="fb-empty-col">하위 폴더 없음</div>
                ) : (
                  col.dirs.map((d) => (
                    <div
                      key={d.path}
                      className={`fb-row ${col.selected === d.path ? 'selected' : ''}`}
                      onClick={() => void handleSelect(colIdx, d.path)}
                    >
                      <span className="fb-row-name">{d.name}</span>
                      <span className="fb-row-chevron">›</span>
                    </div>
                  ))
                )}
              </div>
            ))
          )}
        </div>

        <div className="fb-footer">
          <span className="fb-footer-path" title={selectedPath}>
            {selectedPath || '경로를 선택하세요'}
          </span>
          <div className="fb-footer-buttons">
            <button type="button" className="fb-btn fb-btn-cancel" onClick={() => setBrowserOpen(false)}>취소</button>
            <button type="button" className="fb-btn fb-btn-ok" onClick={handleConfirm}>폴더 선택</button>
          </div>
        </div>
      </div>
    </div>
  );
}
