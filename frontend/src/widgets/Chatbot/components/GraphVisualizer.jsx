import React, { useEffect, useRef, useState, useMemo } from 'react';
import ForceGraph2D from 'react-force-graph-2d';

const GraphVisualizer = ({ graphData, theme, onNodeClick }) => {
    const fgRef = useRef();
    const containerRef = useRef();
    const [dimensions, setDimensions] = useState({ width: 0, height: 0 });

    const isDark = theme === 'dark';
    const graphBgColor = isDark ? '#2b2b3b' : '#f8f9fa'; 
    const baseLinkColor = isDark ? 'rgba(255,255,255,0.2)' : '#cccccc';

    const getId = (v) => (v && typeof v === 'object' ? v.id : v);

    const pathLinkSet = useMemo(() => {
        const set = new Set();
        const pLinks = graphData?.path?.links || [];
        for (const l of pLinks) {
            const s = getId(l.source);
            const t = getId(l.target);
            if (!s || !t) continue;
            set.add(`${s}__${t}`);
        }
        return set;
    }, [graphData?.path?.links]);

    const isPathLink = (link) => {
        const s = getId(link.source);
        const t = getId(link.target);
        if (!s || !t) return false;
        return pathLinkSet.has(`${s}__${t}`);
    };

    const textBoxColor = 'transparent'; 

    const getNodeColor = (group) => {
        switch (group) {
            case 1: return '#ff6b6b'; 
            case 2: return '#feca57'; 
            case 3: return '#48dbfb'; 
            case 4: return '#1dd1a1';
            default: return '#a5b1c2';
        }
    };

    useEffect(() => {
        if (!containerRef.current) return;
        const resizeObserver = new ResizeObserver((entries) => {
            for (const entry of entries) {
                setDimensions({ width: entry.contentRect.width, height: entry.contentRect.height });
                if (fgRef.current) fgRef.current.zoomToFit(400); 
            }
        });
        resizeObserver.observe(containerRef.current);
        return () => resizeObserver.disconnect();
    }, []);

    useEffect(() => {
        if (fgRef.current && graphData.nodes.length > 0) {
            fgRef.current.d3Force('charge').strength(-400); 
            fgRef.current.d3Force('link').distance(160);
            setTimeout(() => { if (fgRef.current) fgRef.current.zoomToFit(800, 50); }, 500);
        }
    }, [graphData]);

    return (
        <div ref={containerRef} className="graph-container" style={{ width: '100%', height: '100%', background: graphBgColor }}>
            <div className="graph-header-overlay" style={{ color: isDark ? '#adb5bd' : '#666', padding: '20px' }}>※ 지식 그래프</div>
            
            {graphData.nodes.length > 0 ? (
                <ForceGraph2D
                    ref={fgRef}
                    width={dimensions.width}
                    height={dimensions.height}
                    graphData={graphData}
                    onNodeClick={(node, e) => onNodeClick(node, e)}
                    backgroundColor={graphBgColor}
                    linkColor={(link) => (isPathLink(link) ? '#ff4d4f' : baseLinkColor)}
                    nodeRelSize={6}
                    linkWidth={(link) => (isPathLink(link) ? 4 : 1.5)}
                    linkDirectionalArrowLength={(link) => (isPathLink(link) ? 8 : 4)}
                    linkDirectionalArrowRelPos={1}

                    linkCurvature={link => {
                        const getID = (node) => typeof node === 'object' ? node.id : node;
                        const reciprocal = graphData.links.find(l => 
                            getID(l.source) === getID(link.target) && getID(l.target) === getID(link.source)
                        );
                        return reciprocal ? 0.2 : 0;
                    }}

                    linkLabel="label" 
                    linkCanvasObjectMode={() => 'after'}
                    linkCanvasObject={(link, ctx, globalScale) => {
                        const fontSize = 11 / globalScale;
                        ctx.font = `${fontSize}px Sans-Serif`;

                        const start = link.source;
                        const end = link.target;
                        if (typeof start !== 'object' || typeof end !== 'object') return;

                        const dx = end.x - start.x;
                        const dy = end.y - start.y;
                        const dist = Math.sqrt(dx * dx + dy * dy);
                        const angle = Math.atan2(dy, dx);
                        
                        const curvature = link.curvature || 0;
                        const vOffset = (curvature !== 0) ? (dist * curvature * 0.4) : (6 / globalScale) + 6;
                        
                        const textX = (start.x + end.x) / 2 + Math.sin(angle) * vOffset;
                        const textY = (start.y + end.y) / 2 - Math.cos(angle) * vOffset;

                        ctx.save();
                        ctx.translate(textX, textY);
                        
                        let textAngle = angle;
                        if (textAngle > Math.PI / 2 || textAngle < -Math.PI / 2) textAngle += Math.PI;
                        ctx.rotate(textAngle);

                        const label = link.label || "";
                        const textWidth = ctx.measureText(label).width;

                        const path = isPathLink(link);
                        ctx.fillStyle = path
                            ? 'rgba(255, 77, 79, 0.25)'
                            : (isDark ? 'rgba(43, 43, 59, 0.85)' : 'rgba(248, 249, 250, 0.85)');

                        ctx.fillRect(-textWidth/2 - 1, -fontSize/2 - 1, textWidth + 2, fontSize + 2);
                        
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillStyle = isDark ? '#ffffff' : '#333333';
                        ctx.fillText(label, 0, 0);
                        ctx.restore();
                    }}
                        
                    nodeCanvasObject={(node, ctx, globalScale) => {
                        const label = node.name || node.id; 
                        const displayLabel = label.length > 10 ? label.substring(0, 10) + "..." : label;
                        const fontSize = 12 / globalScale;
                        ctx.font = `bold ${fontSize}px Sans-Serif`;
                        const nodeColor = getNodeColor(node.group);
                        ctx.beginPath();
                        ctx.arc(node.x, node.y, 6, 0, 2 * Math.PI, false);
                        ctx.fillStyle = nodeColor;
                        ctx.fill();
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillStyle = isDark ? '#ffffff' : '#333333';
                        ctx.fillText(displayLabel, node.x, node.y + 12);
                    }}
                />
            ) : (
                <div className="empty-graph" style={{ color: isDark ? '#666' : '#adb5bd', marginLeft: '20px' }}>
                    <p>데이터가 없습니다.</p>
                </div>
            )}
        </div>
    );
};

export default GraphVisualizer;