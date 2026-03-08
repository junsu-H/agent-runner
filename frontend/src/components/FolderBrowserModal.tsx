type FolderBrowserModalProps = {
  browserOpen: boolean;
  setBrowserOpen: (open: boolean) => void;
  browsePath: string;
  selectedDir: string | null;
  setSelectedDir: (dir: string | null) => void;
  dirList: { name: string; path: string }[];
  parentPath: string | null;
  loadingDirs: boolean;
  addressInput: string;
  setAddressInput: (input: string) => void;
  fetchDirs: (path: string) => void;
  confirmSelection: () => void;
};

export function FolderBrowserModal(props: FolderBrowserModalProps) {
  const {
    browserOpen, setBrowserOpen, browsePath, selectedDir, setSelectedDir,
    dirList, parentPath, loadingDirs, addressInput, setAddressInput,
    fetchDirs, confirmSelection,
  } = props;

  if (!browserOpen) return null;

  const handleAddressKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') fetchDirs(addressInput);
  };

  return (
    <div className="fb-overlay" onClick={() => setBrowserOpen(false)}>
      <div className="fb-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="fb-titlebar">
          <span className="fb-title">Skill 폴더 선택</span>
          <button type="button" className="fb-close" onClick={() => setBrowserOpen(false)}>&times;</button>
        </div>

        <div className="fb-addressbar">
          {parentPath && (
            <button type="button" className="fb-nav-btn" onClick={() => fetchDirs(parentPath)} title="상위 폴더">
              &uarr;
            </button>
          )}
          <input
            className="fb-address-input"
            value={addressInput}
            onChange={(e) => setAddressInput(e.target.value)}
            onKeyDown={handleAddressKeyDown}
            placeholder="경로 입력 후 Enter"
          />
          <button type="button" className="fb-nav-btn" onClick={() => fetchDirs(addressInput)} title="이동">
            &rarr;
          </button>
        </div>

        <div className="fb-filelist">
          {loadingDirs ? (
            <div className="fb-empty">불러오는 중...</div>
          ) : dirList.length === 0 ? (
            <div className="fb-empty">하위 폴더가 없습니다.</div>
          ) : (
            dirList.map((d) => (
              <div
                key={d.path}
                className={`fb-row ${selectedDir === d.path ? 'selected' : ''}`}
                onClick={() => setSelectedDir(d.path)}
                onDoubleClick={() => fetchDirs(d.path)}
              >
                <span className="fb-row-icon">{'\uD83D\uDCC1'}</span>
                <span className="fb-row-name">{d.name}</span>
              </div>
            ))
          )}
        </div>

        <div className="fb-footer">
          <span className="fb-footer-path" title={selectedDir ?? browsePath}>
            {selectedDir ?? browsePath}
          </span>
          <div className="fb-footer-buttons">
            <button type="button" className="fb-btn fb-btn-cancel" onClick={() => setBrowserOpen(false)}>취소</button>
            <button type="button" className="fb-btn fb-btn-ok" onClick={confirmSelection}>폴더 선택</button>
          </div>
        </div>
      </div>
    </div>
  );
}
