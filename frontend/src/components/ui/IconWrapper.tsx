import { motion, MotionProps } from 'framer-motion';
import { ReactNode } from 'react';

interface IconWrapperProps extends Omit<MotionProps, 'children'> {
  children: ReactNode;
  hoverScale?: number;
  hoverRotate?: number | number[];
  tapScale?: number;
  animate?: boolean;
  animateType?: 'rotate' | 'pulse' | 'bounce';
}

export function IconWrapper({
  children,
  hoverScale = 1.1,
  hoverRotate = 5,
  tapScale = 0.95,
  animate = false,
  animateType = 'rotate',
  ...motionProps
}: IconWrapperProps) {
  const animateProps = animate
    ? animateType === 'rotate'
      ? { rotate: [0, 360], transition: { duration: 20, repeat: Infinity, ease: 'linear' } }
      : animateType === 'pulse'
      ? { scale: [1, 1.1, 1], transition: { duration: 2, repeat: Infinity } }
      : { y: [0, -5, 0], transition: { duration: 1.5, repeat: Infinity } }
    : {};

  return (
    <motion.div
      whileHover={{ scale: hoverScale, rotate: hoverRotate }}
      whileTap={{ scale: tapScale }}
      transition={{ type: 'spring', stiffness: 400, damping: 17 }}
      animate={animateProps}
      className="opacity-100 inline-flex items-center justify-center"
      {...motionProps}
    >
      {children}
    </motion.div>
  );
}

